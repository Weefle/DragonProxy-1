/*
 * GNU LESSER GENERAL PUBLIC LICENSE
 *                       Version 3, 29 June 2007
 *
 * Copyright (C) 2007 Free Software Foundation, Inc. <http://fsf.org/>
 * Everyone is permitted to copy and distribute verbatim copies
 * of this license document, but changing it is not allowed.
 *
 * You can view LICENCE file for details. 
 *
 * @author The Dragonet Team
 */
package org.dragonet.proxy.network.translator.pc;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import org.dragonet.proxy.configuration.Lang;
import org.dragonet.proxy.entity.PEEntityAttribute;
import org.dragonet.proxy.entity.meta.EntityMetaData;
import org.dragonet.proxy.network.CacheKey;
import org.dragonet.proxy.network.PCDownstreamSession;
import org.dragonet.proxy.protocol.PEPacket;
import org.dragonet.proxy.network.UpstreamSession;
import org.dragonet.proxy.network.cache.CachedEntity;
import org.dragonet.proxy.network.translator.IPCPacketTranslator;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import org.dragonet.proxy.protocol.packets.*;
import org.dragonet.proxy.utilities.BlockPosition;
import org.dragonet.proxy.utilities.Constants;
import org.dragonet.proxy.utilities.Vector3F;

public class PCPlayerPositionRotationPacketTranslator
		implements IPCPacketTranslator<ServerPlayerPositionRotationPacket> {
	// vars

	// constructor
	public PCPlayerPositionRotationPacketTranslator() {

	}

	// public
	public PEPacket[] translate(UpstreamSession session, ServerPlayerPositionRotationPacket packet) {
		if (!session.isSpawned()) {
			System.out.println("SPAWNED! ");
			if (session.getDataCache().get(CacheKey.PACKET_JOIN_GAME_PACKET) == null) {
				session.disconnect(session.getProxy().getLang().get(Lang.MESSAGE_REMOTE_ERROR));
				return null;
			}

			ServerJoinGamePacket restored = (ServerJoinGamePacket) session.getDataCache()
					.remove(CacheKey.PACKET_JOIN_GAME_PACKET);
			if (!session.getProxy().getAuthMode().equalsIgnoreCase("online")) {
				StartGamePacket ret = new StartGamePacket();
				ret.rtid = 1L;
				ret.eid = 1L; // Use EID 0 for easier management
				ret.dimension = 0;
				ret.seed = 0;
				ret.generator = 1;
				ret.gamemode = restored.getGameMode() == GameMode.CREATIVE ? 1 : 0;
				ret.spawnPosition = new BlockPosition((int) packet.getX(), (int) packet.getY(), (int) packet.getZ());
				ret.position = new Vector3F((float) packet.getX(), (float) packet.getY() + Constants.PLAYER_HEAD_OFFSET,
						(float) packet.getZ());
				ret.levelId = "";
				ret.worldName = "World";
				ret.commandsEnabled = true;
				ret.defaultPlayerPermission = 2;
				ret.premiumWorldTemplateId = "";
				session.sendPacket(ret);
			}

			UpdateAttributesPacket attr = new UpdateAttributesPacket();
			attr.rtid = 1L;
			attr.entries = new PEEntityAttribute[] { PEEntityAttribute.findAttribute(PEEntityAttribute.ABSORPTION),
					PEEntityAttribute.findAttribute(PEEntityAttribute.EXHAUSTION),
					PEEntityAttribute.findAttribute(PEEntityAttribute.HUNGER),
					PEEntityAttribute.findAttribute(PEEntityAttribute.EXPERIENCE_LEVEL),
					PEEntityAttribute.findAttribute(PEEntityAttribute.EXPERIENCE),
					PEEntityAttribute.findAttribute(PEEntityAttribute.EXPERIENCE_LEVEL),
					PEEntityAttribute.findAttribute(PEEntityAttribute.MOVEMENT_SPEED), };
			session.sendPacket(attr);

			AdventureSettingsPacket adv = new AdventureSettingsPacket();
			adv.setFlag(AdventureSettingsPacket.WORLD_IMMUTABLE, restored.getGameMode().equals(GameMode.SPECTATOR));
			// adv.setFlag(AdventureSettingsPacket.ALLOW_FLIGHT, true);
			adv.setFlag(AdventureSettingsPacket.ATTACK_PLAYERS, true);
			adv.setFlag(AdventureSettingsPacket.ATTACK_MOBS, true);
			adv.setFlag(AdventureSettingsPacket.BUILD_AND_MINE, true);
			adv.setFlag(AdventureSettingsPacket.OPERATOR, true);
			adv.setFlag(AdventureSettingsPacket.TELEPORT, true);
			adv.setFlag(AdventureSettingsPacket.NO_CLIP, restored.getGameMode().equals(GameMode.SPECTATOR));
			adv.setFlag(AdventureSettingsPacket.FLYING, false);
			adv.commandsPermission = AdventureSettingsPacket.PERMISSION_OPERATOR;
			adv.playerPermission = 2;
			session.sendPacket(adv);

			SetEntityDataPacket entityData = new SetEntityDataPacket();
			entityData.rtid = 1L;
			entityData.meta = EntityMetaData.createDefault();
			session.sendPacket(entityData);

			if (session.getProxy().getAuthMode().equalsIgnoreCase("online")) {
				MovePlayerPacket pk = new MovePlayerPacket();
				pk.rtid = 1L;
				pk.mode = MovePlayerPacket.MODE_TELEPORT;
				pk.position = new Vector3F((float) packet.getX(), (float) packet.getY() + Constants.PLAYER_HEAD_OFFSET,
						(float) packet.getZ());
				pk.yaw = packet.getYaw();
				pk.pitch = packet.getPitch();
				pk.headYaw = packet.getYaw();
				session.sendPacket(pk);

				CachedEntity cliEntity = session.getEntityCache().getClientEntity();
				cliEntity.x = packet.getX();
				cliEntity.y = packet.getY() + Constants.PLAYER_HEAD_OFFSET;
				cliEntity.z = packet.getZ();
				cliEntity.yaw = packet.getYaw();
				cliEntity.pitch = packet.getPitch();

				/*ChangeDimensionPacket d = new ChangeDimensionPacket();
				d.dimension = 0;
				d.position = new Vector3F((float) packet.getX(), (float) packet.getY() + Constants.PLAYER_HEAD_OFFSET,
						(float) packet.getZ());
				session.sendPacket(d);
				session.sendPacket(new PlayStatusPacket(PlayStatusPacket.PLAYER_SPAWN));*/

				System.out.println("spawning at " + pk.position.toString());
			}

			session.setSpawned();

			session.getEntityCache().getClientEntity().x = packet.getX();
			session.getEntityCache().getClientEntity().y = packet.getY() + Constants.PLAYER_HEAD_OFFSET;
			session.getEntityCache().getClientEntity().z = packet.getZ();

			// send the confirmation
			ClientTeleportConfirmPacket confirm = new ClientTeleportConfirmPacket(packet.getTeleportId());
			((PCDownstreamSession) session.getDownstream()).send(confirm);

			return null;
		}

		MovePlayerPacket pk = new MovePlayerPacket();
		pk.rtid = 1L;
		pk.mode = MovePlayerPacket.MODE_TELEPORT;
		pk.position = new Vector3F((float) packet.getX(), (float) packet.getY() + Constants.PLAYER_HEAD_OFFSET,
				(float) packet.getZ());
		pk.yaw = packet.getYaw();
		pk.pitch = packet.getPitch();
		pk.headYaw = packet.getYaw();
		CachedEntity cliEntity = session.getEntityCache().getClientEntity();
		cliEntity.x = packet.getX();
		cliEntity.y = packet.getY() + Constants.PLAYER_HEAD_OFFSET;
		cliEntity.z = packet.getZ();
		cliEntity.yaw = packet.getYaw();
		cliEntity.pitch = packet.getPitch();

		// session.sendChat(String.format("FORCING TO (%.2f, %.2f, %.2f", packet.getX(),
		// packet.getY(), packet.getZ()));

		// send the confirmation
		ClientTeleportConfirmPacket confirm = new ClientTeleportConfirmPacket(packet.getTeleportId());
		((PCDownstreamSession) session.getDownstream()).send(confirm);

		return new PEPacket[] { pk };
	}

	// private

}
