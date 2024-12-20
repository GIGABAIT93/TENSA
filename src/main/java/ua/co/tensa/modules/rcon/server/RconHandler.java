package ua.co.tensa.modules.rcon.server;

import ua.co.tensa.config.Lang;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class RconHandler extends SimpleChannelInboundHandler<ByteBuf> {

	private static final byte FAILURE = -1;
	private static final byte TYPE_RESPONSE = 0;
	private static final byte TYPE_COMMAND = 2;
	private static final byte TYPE_LOGIN = 3;

	private final String password;

	private boolean loggedIn = false;

	private final RconServer rconServer;

	private final RconCommandSource commandSender;

	public RconHandler(RconServer rconServer, String password) {
		this.rconServer = rconServer;
		this.password = password;
		this.commandSender = new RconCommandSource(rconServer.getServer());
	}

	@Override
	@SuppressWarnings("deprecation")
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
		buf = buf.order(ByteOrder.LITTLE_ENDIAN);
		if (buf.readableBytes() < 8) {
			return;
		}

		int requestId = buf.readInt();
		int type = buf.readInt();

		byte[] payloadData = new byte[buf.readableBytes() - 2];
		buf.readBytes(payloadData);
		String payload = new String(payloadData, StandardCharsets.UTF_8);

		buf.readBytes(2); // two byte padding

		if (type == TYPE_LOGIN) {
			handleLogin(ctx, payload, requestId);
		} else if (type == TYPE_COMMAND) {
			handleCommand(ctx, payload, requestId);
		} else {
			sendLargeResponse(ctx, requestId, Lang.unknown_request.getClean() + " " + Integer.toHexString(type));
		}
	}

	private void handleLogin(ChannelHandlerContext ctx, String payload, int requestId) {
		if (password.equals(payload)) {
			loggedIn = true;
			sendResponse(ctx, requestId, TYPE_COMMAND, "");
		} else {
			loggedIn = false;
			sendResponse(ctx, FAILURE, TYPE_COMMAND, "");
		}
	}

	private void handleCommand(ChannelHandlerContext ctx, String payload, int requestId) {
		if (!loggedIn) {
			sendResponse(ctx, FAILURE, TYPE_COMMAND, "");
			return;
		}
		boolean stop = false;
		boolean success;
		String message;
		String ip = ctx.channel().remoteAddress().toString().replace("/", "");
		Message.info(Lang.rcon_connect_notify.getClean().replace("{address}", ip).replace("{command}", payload));

		Tensa.server.getAllPlayers().forEach(p -> {
			if (p.getPermissionValue("TENSA.rcon.notify").asBoolean()) {
				p.sendMessage(Lang.rcon_connect_notify.replace("{address}", ip, "{command}", payload));
			}
		});

		if (payload.equalsIgnoreCase("end") || payload.equalsIgnoreCase("stop")) {
			stop = true;
			success = true;
			message = "Shutting down the proxy...";
		} else {
			try {
				success = rconServer.getServer().getCommandManager().executeAsync(commandSender, payload).join();
				if (success) {
					message = commandSender.flush();
				} else {
					message = Lang.no_command.getClean();
				}
			} catch (Exception e) {
				Message.error(e.getMessage());
				success = false;
				message = Lang.unknown_error.getClean();
			}
		}

		if (!success) {
			message = String.format(Lang.error_executing.getClean() + " %s (%s)", payload, message);
		}

		if (!RconServerModule.isColored()) {
			message = RconServerModule.stripColor(message);
		}

		sendLargeResponse(ctx, requestId, message);

		if (stop) {
			Tensa.server.shutdown();
		}
	}

	private void sendResponse(ChannelHandlerContext ctx, int requestId, int type, String payload) {
		@SuppressWarnings("deprecation")
		ByteBuf buf = ctx.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN);
		buf.writeInt(requestId);
		buf.writeInt(type);
		buf.writeBytes(payload.getBytes(StandardCharsets.UTF_8));
		buf.writeByte(0);
		buf.writeByte(0);
		ctx.write(buf);
	}

	private void sendLargeResponse(ChannelHandlerContext ctx, int requestId, String payload) {
		if (payload.isEmpty()) {
			sendResponse(ctx, requestId, TYPE_RESPONSE, "");
			return;
		}

		int start = 0;
		while (start < payload.length()) {
			int length = payload.length() - start;
			int truncated = Math.min(length, 2048);

			sendResponse(ctx, requestId, TYPE_RESPONSE, payload.substring(start, truncated));
			start += truncated;
		}
	}
}
