package com.amethystclient.presence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A minimal client for Discord's local IPC socket: the named pipe {@code \\.\pipe\discord-ipc-N} on
 * Windows, a Unix socket elsewhere. Each message is a frame of opcode + length (both little-endian
 * ints) + JSON. Only what Rich Presence needs is implemented: the handshake and SET_ACTIVITY.
 */
final class DiscordIpc implements Closeable {
	private static final int OP_HANDSHAKE = 0;
	private static final int OP_FRAME = 1;
	private static final int OP_CLOSE = 2;

	private final Transport transport;

	private DiscordIpc(Transport transport) {
		this.transport = transport;
	}

	/** Connects to the first running Discord client and performs the handshake. */
	static DiscordIpc connect(String clientId) throws IOException {
		IOException last = new IOException("Discord is not running");
		for (int i = 0; i < 10; i++) {
			Transport transport;
			try {
				transport = Transport.open(i);
			} catch (IOException e) {
				last = e;
				continue;
			}
			DiscordIpc ipc = new DiscordIpc(transport);
			try {
				JsonObject handshake = new JsonObject();
				handshake.addProperty("v", 1);
				handshake.addProperty("client_id", clientId);
				ipc.send(OP_HANDSHAKE, handshake);
				ipc.receive(); // READY
				return ipc;
			} catch (IOException e) {
				ipc.close();
				last = e;
			}
		}
		throw last;
	}

	/** Sets this process's activity, or clears it when {@code activity} is null. */
	void setActivity(JsonObject activity) throws IOException {
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		if (activity != null) {
			args.add("activity", activity);
		}
		JsonObject command = new JsonObject();
		command.addProperty("cmd", "SET_ACTIVITY");
		command.add("args", args);
		command.addProperty("nonce", UUID.randomUUID().toString());
		send(OP_FRAME, command);
		JsonObject response = receive();
		JsonElement event = response.get("evt");
		if (event != null && !event.isJsonNull() && "ERROR".equals(event.getAsString())) {
			throw new IOException("Discord rejected the activity: " + response.get("data"));
		}
	}

	private void send(int op, JsonObject payload) throws IOException {
		byte[] json = payload.toString().getBytes(StandardCharsets.UTF_8);
		ByteBuffer frame = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
		frame.putInt(op).putInt(json.length).put(json);
		transport.write(frame.array());
	}

	private JsonObject receive() throws IOException {
		byte[] header = new byte[8];
		transport.readFully(header);
		ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
		int op = buffer.getInt();
		int length = buffer.getInt();
		if (length < 0 || length > 1 << 20) {
			throw new IOException("Bad Discord IPC frame length " + length);
		}
		byte[] json = new byte[length];
		transport.readFully(json);
		String text = new String(json, StandardCharsets.UTF_8);
		if (op == OP_CLOSE) {
			throw new IOException("Discord closed the connection: " + text);
		}
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@Override
	public void close() {
		try {
			transport.close();
		} catch (IOException ignored) {
		}
	}

	private interface Transport extends Closeable {
		void write(byte[] bytes) throws IOException;

		void readFully(byte[] bytes) throws IOException;

		static Transport open(int index) throws IOException {
			String name = "discord-ipc-" + index;
			if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
				return new PipeTransport(new RandomAccessFile("\\\\.\\pipe\\" + name, "rw"));
			}
			for (Path dir : unixSocketDirs()) {
				Path socket = dir.resolve(name);
				if (Files.exists(socket)) {
					return new SocketTransport(SocketChannel.open(UnixDomainSocketAddress.of(socket)));
				}
			}
			throw new IOException("No Discord socket " + name);
		}

		private static List<Path> unixSocketDirs() {
			List<Path> dirs = new ArrayList<>();
			for (String variable : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
				String value = System.getenv(variable);
				if (value != null && !value.isEmpty()) {
					Path dir = Path.of(value);
					dirs.add(dir);
					// Flatpak and Snap builds of Discord put the socket in a subfolder.
					dirs.add(dir.resolve("app/com.discordapp.Discord"));
					dirs.add(dir.resolve("snap.discord"));
				}
			}
			dirs.add(Path.of("/tmp"));
			return dirs;
		}
	}

	private record PipeTransport(RandomAccessFile pipe) implements Transport {
		@Override
		public void write(byte[] bytes) throws IOException {
			pipe.write(bytes);
		}

		@Override
		public void readFully(byte[] bytes) throws IOException {
			pipe.readFully(bytes);
		}

		@Override
		public void close() throws IOException {
			pipe.close();
		}
	}

	private record SocketTransport(SocketChannel channel) implements Transport {
		@Override
		public void write(byte[] bytes) throws IOException {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining()) {
				channel.write(buffer);
			}
		}

		@Override
		public void readFully(byte[] bytes) throws IOException {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining()) {
				if (channel.read(buffer) < 0) {
					throw new IOException("Discord closed the connection");
				}
			}
		}

		@Override
		public void close() throws IOException {
			channel.close();
		}
	}
}
