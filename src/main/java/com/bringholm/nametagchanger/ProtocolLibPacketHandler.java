package com.bringholm.nametagchanger;

import com.bringholm.reflectutil.v1_1.ReflectUtil;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.*;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * The packet handler implementation using ProtocolLib
 * @author AlvinB
 */
public class ProtocolLibPacketHandler extends PacketAdapter implements IPacketHandler {

    private static final Class<?> ENTITY_PLAYER = ReflectUtil.getNMSClass("EntityPlayer").getOrThrow();
    private static final Method GET_HANDLE = ReflectUtil.getMethod(ReflectUtil.getCBClass("entity.CraftPlayer").getOrThrow(), "getHandle").getOrThrow();
    private static final Field PING = ReflectUtil.getField(ENTITY_PLAYER, "ping").getOrThrow();

    ProtocolLibPacketHandler(Plugin plugin) {
        super(plugin, PacketType.Play.Server.PLAYER_INFO);
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    @Override
    public void onPacketSending(PacketEvent e) {
        List<PlayerInfoData> list = Lists.newArrayList();
        boolean modified = false;
        for (PlayerInfoData infoData : e.getPacket().getPlayerInfoDataLists().read(0)) {
            if (NameTagChanger.INSTANCE.players.containsKey(infoData.getProfile().getUUID())) {
                UUID uuid = infoData.getProfile().getUUID();
                WrappedChatComponent displayName = infoData.getDisplayName() == null ? WrappedChatComponent.fromText(Bukkit.getPlayer(uuid).getPlayerListName()) : infoData.getDisplayName();
                WrappedGameProfile gameProfile = getProtocolLibProfileWrapper(NameTagChanger.INSTANCE.players.get(uuid));
                PlayerInfoData newInfoData = new PlayerInfoData(gameProfile, infoData.getLatency(), infoData.getGameMode(), displayName);
                list.add(newInfoData);
                modified = true;
            } else {
                list.add(infoData);
            }
        }
        if (modified) {
            e.getPacket().getPlayerInfoDataLists().write(0, list);
        }
    }

    @Override
    public void sendTabListRemovePacket(Player playerToRemove, Player seer) {
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);
        packet.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
        PlayerInfoData playerInfoData = new PlayerInfoData(WrappedGameProfile.fromPlayer(playerToRemove), 0, EnumWrappers.NativeGameMode.NOT_SET, null);
        packet.getPlayerInfoDataLists().write(0, Collections.singletonList(playerInfoData));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(seer, packet);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendTabListAddPacket(Player playerToAdd, GameProfileWrapper newProfile, Player seer) {
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);
        int ping = (int) ReflectUtil.getFieldValue(ReflectUtil.invokeMethod(playerToAdd, GET_HANDLE).getOrThrow(), PING).getOrThrow();
        packet.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
        PlayerInfoData playerInfoData = new PlayerInfoData(getProtocolLibProfileWrapper(newProfile), ping, EnumWrappers.NativeGameMode.fromBukkit(playerToAdd.getGameMode()), WrappedChatComponent.fromText(playerToAdd.getPlayerListName()));
        packet.getPlayerInfoDataLists().write(0, Collections.singletonList(playerInfoData));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(seer, packet);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendEntityDestroyPacket(Player playerToDestroy, Player seer) {
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        packet.getIntegerArrays().write(0, new int[] {playerToDestroy.getEntityId()});
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(seer, packet);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendNamedEntitySpawnPacket(Player playerToSpawn, Player seer) {
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        packet.getIntegers().write(0, playerToSpawn.getEntityId());
        packet.getUUIDs().write(0, playerToSpawn.getUniqueId());
        packet.getDoubles().write(0, playerToSpawn.getLocation().getX());
        packet.getDoubles().write(1, playerToSpawn.getLocation().getY());
        packet.getDoubles().write(2, playerToSpawn.getLocation().getZ());
        packet.getBytes().write(0, (byte) (playerToSpawn.getLocation().getYaw() * 256F / 360F));
        packet.getBytes().write(1, (byte) (playerToSpawn.getLocation().getPitch() * 256F / 360F));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(seer, packet);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public GameProfileWrapper getDefaultPlayerProfile(Player player) {
        WrappedGameProfile wrappedGameProfile = WrappedGameProfile.fromPlayer(player);
        GameProfileWrapper wrapper = new GameProfileWrapper(wrappedGameProfile.getUUID(), wrappedGameProfile.getName());
        for (Map.Entry<String, Collection<WrappedSignedProperty>> entry : wrappedGameProfile.getProperties().asMap().entrySet()) {
            for (WrappedSignedProperty wrappedSignedProperty : entry.getValue()) {
                wrapper.getProperties().put(entry.getKey(), new GameProfileWrapper.PropertyWrapper(wrappedSignedProperty.getName(), wrappedSignedProperty.getValue(), wrappedSignedProperty.getSignature()));
            }
        }
        return wrapper;
    }

    private static WrappedGameProfile getProtocolLibProfileWrapper(GameProfileWrapper wrapper) {
        WrappedGameProfile wrappedGameProfile = new WrappedGameProfile(wrapper.getUUID(), wrapper.getName());
        for (Map.Entry<String, Collection<GameProfileWrapper.PropertyWrapper>> entry : wrapper.getProperties().asMap().entrySet()) {
            for (GameProfileWrapper.PropertyWrapper propertyWrapper : entry.getValue()) {
                wrappedGameProfile.getProperties().put(entry.getKey(), new WrappedSignedProperty(propertyWrapper.getName(), propertyWrapper.getValue(), propertyWrapper.getSignature()));
            }
        }
        return wrappedGameProfile;
    }

    @Override
    public void shutdown() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
    }
}
