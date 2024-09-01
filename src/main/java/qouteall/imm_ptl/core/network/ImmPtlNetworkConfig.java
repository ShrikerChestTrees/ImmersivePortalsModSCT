package qouteall.imm_ptl.core.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.mixin.common.other_sync.IEServerConfigurationPacketListenerImpl;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;

import java.util.function.Consumer;

public class ImmPtlNetworkConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static record ModVersion(
        int major, int minor, int patch
    ) {
        // for dev env
        public static final ModVersion OTHER = new ModVersion(0, 0, 0);
        
        public static ModVersion read(FriendlyByteBuf buf) {
            int major = buf.readVarInt();
            int minor = buf.readVarInt();
            int patch = buf.readVarInt();
            return new ModVersion(major, minor, patch);
        }
        
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(major);
            buf.writeVarInt(minor);
            buf.writeVarInt(patch);
        }
        
        @Override
        public String toString() {
            return "%d.%d.%d".formatted(major, minor, patch);
        }
        
        public boolean isNormalVersion() {
            return !OTHER.equals(this);
        }
        
        public boolean isCompatibleWith(ModVersion another) {
            return major == another.major && minor == another.minor;
        }
    }
    
    public static ModVersion immPtlVersion;
    
    public static record ImmPtlConfigurationTask(
    ) implements ConfigurationTask {
        public static final ConfigurationTask.Type TYPE =
            new ConfigurationTask.Type("iportal:config");
        
        @Override
        public void start(Consumer<Packet<?>> consumer) {
            consumer.accept(
                ServerConfigurationNetworking.createS2CPacket(new S2CConfigStartPacket(
                    immPtlVersion
                ))
            );
        }
        
        @Override
        public @NotNull Type type() {
            return TYPE;
        }
    }
    
    public static record S2CConfigStartPacket(
        ModVersion versionFromServer
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<S2CConfigStartPacket> TYPE =
            new CustomPacketPayload.Type<>(
                McHelper.newResourceLocation("iportal:config_packet")
            );
        
        public static final StreamCodec<FriendlyByteBuf, S2CConfigStartPacket> CODEC = StreamCodec.of(
            (b, p) -> p.write(b), S2CConfigStartPacket::read
        );
        
        public static S2CConfigStartPacket read(FriendlyByteBuf buf) {
            ModVersion info = ModVersion.read(buf);
            return new S2CConfigStartPacket(info);
        }
        
        public void write(FriendlyByteBuf buf) {
            versionFromServer.write(buf);
        }
        
        // handled on client side
        @Environment(EnvType.CLIENT)
        public void handle(ClientConfigurationNetworking.Context context) {
            LOGGER.info(
                "Client received ImmPtl config packet. Server mod version: {}", versionFromServer
            );
            
            serverVersion = versionFromServer;
            context.responseSender().sendPacket(new C2SConfigCompletePacket(
                immPtlVersion, IPConfig.getConfig().clientTolerantVersionMismatchWithServer
            ));
        }
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    public record C2SConfigCompletePacket(
        ModVersion versionFromClient,
        boolean clientTolerantVersionMismatch
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<C2SConfigCompletePacket> TYPE =
            new CustomPacketPayload.Type<>(
                McHelper.newResourceLocation("iportal:configure_complete")
            );
        public static final StreamCodec<FriendlyByteBuf, C2SConfigCompletePacket> CODEC = StreamCodec.of(
            (b, p) -> p.write(b), C2SConfigCompletePacket::read
        );
        
        public static C2SConfigCompletePacket read(FriendlyByteBuf buf) {
            ModVersion info = ModVersion.read(buf);
            boolean clientTolerantVersionMismatch = buf.readBoolean();
            return new C2SConfigCompletePacket(info, clientTolerantVersionMismatch);
        }
        
        public void write(FriendlyByteBuf buf) {
            versionFromClient.write(buf);
            buf.writeBoolean(clientTolerantVersionMismatch);
        }
        
        // handled on server side
        public void handle(
            ServerConfigurationNetworking.Context context
        ) {
            ServerConfigurationPacketListenerImpl networkHandler = context.networkHandler();
            
            GameProfile gameProfile =
                ((IEServerConfigurationPacketListenerImpl) networkHandler).ip_getGameProfile();
            
            LOGGER.info(
                "Server received ImmPtl config packet. Mod version: {} Player: {} {}",
                versionFromClient, gameProfile.getName(), gameProfile.getId()
            );
            
            if (versionFromClient.isNormalVersion() && immPtlVersion.isNormalVersion()) {
                if ((versionFromClient.major != immPtlVersion.major ||
                    versionFromClient.minor != immPtlVersion.minor) &&
                    !IPConfig.getConfig().serverTolerantVersionMismatchWithClient &&
                    !clientTolerantVersionMismatch
                ) {
                    networkHandler.disconnect(Component.translatable(
                        "imm_ptl.mod_major_minor_version_mismatch",
                        immPtlVersion.toString(),
                        versionFromClient.toString()
                    ));
                    LOGGER.info(
                        """
                            Disconnecting client because of ImmPtl version difference (only patch version difference is tolerated).
                            Game Profile: {}
                            Client ImmPtl version: {}
                            Server ImmPtl version: {}""",
                        gameProfile, versionFromClient, immPtlVersion
                    );
                    return;
                }
            }
            
            networkHandler.completeTask(ImmPtlConfigurationTask.TYPE);
        }
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    public static void init() {
        immPtlVersion = O_O.getImmPtlVersion();
        
        LOGGER.info("Immersive Portals Core version {}", immPtlVersion);
        
        PayloadTypeRegistry.configurationS2C().register(
            S2CConfigStartPacket.TYPE, S2CConfigStartPacket.CODEC
        );
        
        PayloadTypeRegistry.configurationC2S().register(
            C2SConfigCompletePacket.TYPE, C2SConfigCompletePacket.CODEC
        );
        
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, S2CConfigStartPacket.TYPE)) {
                handler.addTask(new ImmPtlConfigurationTask());
            }
            else {
                if (server.isDedicatedServer()) {
                    if (IPConfig.getConfig().serverRejectClientWithoutImmPtl) {
                        // cannot use translation key here
                        // because the translation does not exist on client without the mod
                        handler.disconnect(Component.literal(
                            """
                                The server detected that client does not install Immersive Portals mod.
                                A server with Immersive Portals mod only works with the clients that have it.
                                
                                (Note: The networking sync may be interfered by Essential mod or other mods. When you are using these mods, the detection may malfunction. In this case, you can disable networking check in the server side by changing `serverRejectClientWithoutImmPtl` to `false` in the server's config file `config/immersive_portals.json` and restart.)
                                """
                        ));
                    }
                    else {
                        GameProfile gameProfile =
                            ((IEServerConfigurationPacketListenerImpl) handler).ip_getGameProfile();
                        
                        LOGGER.warn(
                            "Fabric API's sendable channel sync detected that client does not install ImmPtl. {} {}",
                            gameProfile.getName(), gameProfile.getId()
                        );
                    }
                }
                else {
                    LOGGER.error("ImmPtl configuration channel is non-sendable from Fabric API in integrated server. Fabric API sendable channel sync is interfered.");
                }
            }
        });
        
        ServerConfigurationNetworking.registerGlobalReceiver(
            C2SConfigCompletePacket.TYPE,
            C2SConfigCompletePacket::handle
        );
    }
    
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        // ClientConfigurationNetworking.ConfigurationPacketHandler does not provide
        // ClientConfigurationPacketListenerImpl argument
        ClientConfigurationNetworking.registerGlobalReceiver(
            S2CConfigStartPacket.TYPE,
            S2CConfigStartPacket::handle
        );
        
        ClientLoginConnectionEvents.INIT.register(
            (handler, client) -> {
                LOGGER.info("Client login init");
                // if the config packet is not received,
                // serverProtocolInfo will always be nul
                // it will become not null when receiving ImmPtl config packet
                serverVersion = null;
            }
        );
        
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            onClientJoin();
        });
    }
    
    private static void onClientJoin() {
        if (serverVersion == null) {
            warnServerMissingImmPtl();
        }
        else {
            if (serverVersion.isNormalVersion() &&
                immPtlVersion.isNormalVersion() &&
                !serverVersion.equals(immPtlVersion)
            ) {
                if (IPConfig.getConfig().shouldDisplayWarning("mod_version_mismatch")) {
                    MutableComponent text =
                        Component.translatable(
                            "imm_ptl.mod_patch_version_mismatch",
                            Component.literal(serverVersion.toString())
                                .withStyle(ChatFormatting.GOLD),
                            Component.literal(immPtlVersion.toString())
                                .withStyle(ChatFormatting.GOLD)
                        ).append(
                            IPMcHelper.getDisableWarningText("mod_version_mismatch")
                        );
                    CHelper.printChat(text);
                }
            }
        }
    }
    
    // used on client
    private static @Nullable ImmPtlNetworkConfig.ModVersion serverVersion = null;
    
    // should be called from client
    public static boolean doesServerHaveImmPtl() {
        return serverVersion != null;
    }
    
    private static void warnServerMissingImmPtl() {
        Minecraft.getInstance().execute(() -> {
            CHelper.printChat(Component.translatable("imm_ptl.server_missing_immptl"));
        });
    }
}
