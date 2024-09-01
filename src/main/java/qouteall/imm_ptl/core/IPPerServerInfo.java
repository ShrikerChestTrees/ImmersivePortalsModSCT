package qouteall.imm_ptl.core;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ducks.IEMinecraftServer;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGenManager;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.q_misc_util.dimension.DimIntIdMap;
import qouteall.q_misc_util.my_util.MyTaskList;

public class IPPerServerInfo {
    public final MyTaskList taskList = new MyTaskList();
    
    public @Nullable DimIntIdMap dimIntIdMap;
    
    public @Nullable CustomPortalGenManager customPortalGenManager;
    
    public final ServerTeleportationManager teleportationManager = new ServerTeleportationManager();
    
    public final PortalWandInteraction portalWandInteraction = new PortalWandInteraction();
    
    public static IPPerServerInfo of(MinecraftServer server) {
        return ((IEMinecraftServer) server).ip_getPerServerInfo();
    }
}
