package qouteall.imm_ptl.core.portal;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;

import java.util.UUID;
import java.util.function.Consumer;

// the additional features of a portal
public class PortalExtension {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * @param portal
     * @return the portal extension object
     */
    public static PortalExtension get(Portal portal) {
        if (portal.extension == null) {
            portal.extension = new PortalExtension();
        }
        return portal.extension;
    }
    
    public static void init() {
        Portal.CLIENT_PORTAL_TICK_SIGNAL.register(portal -> {
            get(portal).tick(portal);
        });
        
        Portal.SERVER_PORTAL_TICK_SIGNAL.register(portal -> {
            get(portal).tick(portal);
        });
        
        Portal.READ_PORTAL_DATA_SIGNAL.register((portal, tag) -> {
            get(portal).readFromNbt(tag);
        });
        
        Portal.WRITE_PORTAL_DATA_SIGNAL.register((portal, tag) -> {
            get(portal).writeToNbt(tag);
        });
    }
    
    /**
     * If positive, the player that's touching the portal will be accelerated
     * If negative, the player that's touching the portal and moving quickly will
     * be decelerated
     */
    public double motionAffinity = 0;
    
    /**
     * If true, when the player comes out from the portal and get stuck in block
     * the player will be smoothly levitated to avoid falling through floor
     */
    public boolean adjustPositionAfterTeleport = true;
    
    public boolean bindCluster = true;
    
    // these are stored in data
    @Nullable
    public UUID reversePortalId;
    @Nullable
    public UUID flippedPortalId;
    @Nullable
    public UUID parallelPortalId;
    
    // these are initialized at runtime
    @Nullable
    public Portal reversePortal;
    @Nullable
    public Portal flippedPortal;
    @Nullable
    public Portal parallelPortal;
    
    public PortalExtension() {
    
    }
    
    private void readFromNbt(CompoundTag compoundTag) {
        if (compoundTag.contains("motionAffinity")) {
            motionAffinity = compoundTag.getDouble("motionAffinity");
        }
        else {
            motionAffinity = 0;
        }
        if (compoundTag.contains("adjustPositionAfterTeleport")) {
            adjustPositionAfterTeleport = compoundTag.getBoolean("adjustPositionAfterTeleport");
        }
        else {
            adjustPositionAfterTeleport = true;
        }
        
        if (compoundTag.contains("bindCluster")) {
            bindCluster = compoundTag.getBoolean("bindCluster");
        }
        else {
            bindCluster = true;
        }
        
        if (compoundTag.hasUUID("reversePortalId")) {
            reversePortalId = compoundTag.getUUID("reversePortalId");
        }
        else {
            reversePortalId = null;
        }
        if (compoundTag.hasUUID("flippedPortalId")) {
            flippedPortalId = compoundTag.getUUID("flippedPortalId");
        }
        else {
            flippedPortalId = null;
        }
        if (compoundTag.hasUUID("parallelPortalId")) {
            parallelPortalId = compoundTag.getUUID("parallelPortalId");
        }
        else {
            parallelPortalId = null;
        }
    }
    
    private void writeToNbt(CompoundTag compoundTag) {
        if (motionAffinity != 0) {
            compoundTag.putDouble("motionAffinity", motionAffinity);
        }
        compoundTag.putBoolean("adjustPositionAfterTeleport", adjustPositionAfterTeleport);
        compoundTag.putBoolean("bindCluster", bindCluster);
        if (reversePortalId != null) {
            compoundTag.putUUID("reversePortalId", reversePortalId);
        }
        if (flippedPortalId != null) {
            compoundTag.putUUID("flippedPortalId", flippedPortalId);
        }
        if (parallelPortalId != null) {
            compoundTag.putUUID("parallelPortalId", parallelPortalId);
        }
    }
    
    private void tick(Portal portal) {
        if (portal.level().isClientSide()) {
            updateClusterStatusClient(portal);
        }
        else {
            updateClusterStatusServer(portal);
        }
    }
    
    private void updateClusterStatusServer(Portal portal) {
        if (portal instanceof Mirror) {
            return;
        }
        
        boolean needsUpdate = false;
        
        if (bindCluster) {
            if (flippedPortal != null) {
                if (flippedPortal.isRemoved()) {
                    flippedPortal = null;
                }
            }
            if (reversePortal != null) {
                if (reversePortal.isRemoved()) {
                    reversePortal = null;
                }
            }
            if (parallelPortal != null) {
                if (parallelPortal.isRemoved()) {
                    parallelPortal = null;
                }
            }
            
            if (flippedPortalId != null) {
                // if the id is not null, find the portal entity
                if (flippedPortal == null) {
                    Entity e = ((IEWorld) portal.level()).portal_getEntityLookup().get(flippedPortalId);
                    if (e instanceof Portal p) {
                        flippedPortal = p;
                    }
                    else {
                        flippedPortalId = null;
                        needsUpdate = true;
                    }
                }
            }
            if (flippedPortalId == null) {
                // if the id is null, find the portal from world
                flippedPortal = PortalManipulation.findFlippedPortal(portal);
                if (flippedPortal != null) {
                    flippedPortalId = flippedPortal.getUUID();
                    needsUpdate = true;
                }
            }
            
            if (reversePortalId != null) {
                if (reversePortal == null) {
                    Entity e = ((IEWorld) portal.getDestWorld()).portal_getEntityLookup().get(reversePortalId);
                    if (e instanceof Portal p) {
                        reversePortal = p;
                    }
                    else {
                        if (portal.isOtherSideChunkLoaded()) {
                            LOGGER.info("portal linking break {}", portal);
                            reversePortalId = null;
                            needsUpdate = true;
                        }
                    }
                }
            }
            if (reversePortalId == null) {
                reversePortal = PortalManipulation.findReversePortal(portal);
                if (reversePortal != null) {
                    reversePortalId = reversePortal.getUUID();
                    needsUpdate = true;
                }
            }
            
            if (parallelPortalId != null) {
                if (parallelPortal == null) {
                    Entity e = ((IEWorld) portal.getDestWorld()).portal_getEntityLookup().get(parallelPortalId);
                    if (e instanceof Portal p) {
                        parallelPortal = p;
                    }
                    else {
                        if (portal.isOtherSideChunkLoaded()) {
                            LOGGER.info("portal linking break {}", portal);
                            parallelPortalId = null;
                            needsUpdate = true;
                        }
                    }
                }
            }
            if (parallelPortalId == null) {
                parallelPortal = PortalManipulation.findParallelPortal(portal);
                if (parallelPortal != null) {
                    parallelPortalId = parallelPortal.getUUID();
                    needsUpdate = true;
                }
            }
        }
        else {
            flippedPortal = null;
            reversePortal = null;
            parallelPortal = null;
            flippedPortalId = null;
            reversePortalId = null;
            parallelPortalId = null;
        }
        
        if (flippedPortal != null) {
            PortalExtension.get(flippedPortal).bindCluster = true;
        }
        if (reversePortal != null) {
            PortalExtension.get(reversePortal).bindCluster = true;
        }
        if (parallelPortal != null) {
            PortalExtension.get(parallelPortal).bindCluster = true;
        }
        
        // in older versions, the parallel portal could be itself
        // correct it
        if (flippedPortal == portal) {
            flippedPortal = null;
            flippedPortalId = null;
            needsUpdate = true;
        }
        if (reversePortal == portal) {
            reversePortal = null;
            reversePortalId = null;
            needsUpdate = true;
        }
        if (parallelPortal == portal) {
            parallelPortal = null;
            parallelPortalId = null;
            needsUpdate = true;
        }
        
        if (needsUpdate) {
            portal.reloadAndSyncToClient();
        }
    }
    
    
    private void updateClusterStatusClient(Portal portal) {
        if (bindCluster) {
            if (flippedPortalId != null) {
                // if the id is not null, find the portal
                Entity e = ((IEWorld) portal.level()).portal_getEntityLookup().get(flippedPortalId);
                if (e instanceof Portal p) {
                    flippedPortal = p;
                }
            }
            else {
                flippedPortal = null;
            }
            
            if (reversePortalId != null) {
                Entity e = ((IEWorld) portal.getDestWorld()).portal_getEntityLookup().get(reversePortalId);
                if (e instanceof Portal p) {
                    reversePortal = p;
                }
            }
            else {
                reversePortal = null;
            }
            
            if (parallelPortalId != null) {
                Entity e = ((IEWorld) portal.getDestWorld()).portal_getEntityLookup().get(parallelPortalId);
                if (e instanceof Portal p) {
                    parallelPortal = p;
                }
            }
            else {
                parallelPortal = null;
            }
        }
        else {
            flippedPortal = null;
            reversePortal = null;
            parallelPortal = null;
        }
    }
    
    // works on both client and server
    public void rectifyClusterPortals(Portal portal, boolean sync) {
        
        portal.animation.defaultAnimation.inverseScale = false;
        
        if (flippedPortal != null) {
            flippedPortal = ServerTeleportationManager.teleportRegularEntityTo(
                flippedPortal,
                portal.level().dimension(),
                portal.getOriginPos()
            );
            
            flippedPortal.setDestDim(portal.getDestDim());
            flippedPortal.setOriginPos(portal.getOriginPos());
            flippedPortal.setDestination(portal.getDestPos());
            
            flippedPortal.setAxisW(portal.getAxisW().scale(-1));
            flippedPortal.setAxisH(portal.getAxisH());
            
            flippedPortal.setScaling(portal.getScaling());
            flippedPortal.setRotation(portal.getRotation());
            
            flippedPortal.setWidth(portal.getWidth());
            flippedPortal.setHeight(portal.getHeight());
            flippedPortal.setThickness(portal.getThickness());
            
            PortalManipulation.copyAdditionalProperties(flippedPortal, portal, false);
            
            flippedPortal.animation.defaultAnimation.inverseScale = false;
            
            flippedPortal.setPortalShape(portal.getPortalShape().getFlipped());
            
            if (sync) {
                flippedPortal.reloadAndSyncToClientNextTick();
            }
        }
        
        if (reversePortal != null) {
            if (portal.getDestDim() != reversePortal.level().dimension()) {
                LOGGER.info(
                    "Moving reverse portal across dimension {} {}",
                    portal, reversePortal
                );
            }
            
            reversePortal = ServerTeleportationManager.teleportRegularEntityTo(
                reversePortal,
                portal.getDestDim(),
                portal.getDestPos()
            );
            reversePortalId = reversePortal.getUUID();
            
            reversePortal.setDestDim(portal.getOriginDim());
            reversePortal.setOriginPos(portal.getDestPos());
            reversePortal.setDestination(portal.getOriginPos());
            
            reversePortal.setAxisW(portal.transformLocalVecNonScale(portal.getAxisW().scale(-1)));
            reversePortal.setAxisH(portal.transformLocalVecNonScale(portal.getAxisH()));
            reversePortal.setScaling(1.0 / portal.getScaling());
            if (portal.getRotation() != null) {
                reversePortal.setRotation(portal.getRotation().getConjugated());
            }
            else {
                reversePortal.setRotation(null);
            }
            
            reversePortal.setWidth(portal.getWidth() * portal.getScale());
            reversePortal.setHeight(portal.getHeight() * portal.getScale());
            reversePortal.setThickness(portal.getThickness() * portal.getScale());
            
            PortalManipulation.copyAdditionalProperties(reversePortal, portal, false);
            
            reversePortal.animation.defaultAnimation.inverseScale = true;
            
            reversePortal.setPortalShape(portal.getPortalShape().getReverse());
            
            if (sync) {
                reversePortal.reloadAndSyncToClientNextTick();
            }
        }
        
        if (parallelPortal != null) {
            if (portal.getDestDim() != parallelPortal.level().dimension()) {
                LOGGER.info(
                    "Moving parallel portal across dimension {} {}", portal, parallelPortal
                );
            }
            
            parallelPortal = ServerTeleportationManager.teleportRegularEntityTo(
                parallelPortal,
                portal.getDestDim(),
                portal.getDestPos()
            );
            parallelPortalId = parallelPortal.getUUID();
            
            parallelPortal.setDestDim(portal.getOriginDim());
            parallelPortal.setOriginPos(portal.getDestPos());
            parallelPortal.setDestination(portal.getOriginPos());
            
            parallelPortal.setAxisW(portal.transformLocalVecNonScale(portal.getAxisW()));
            parallelPortal.setAxisH(portal.transformLocalVecNonScale(portal.getAxisH()));
            parallelPortal.setScaling(1.0 / portal.getScaling());
            if (portal.getRotation() != null) {
                parallelPortal.setRotation(portal.getRotation().getConjugated());
            }
            else {
                parallelPortal.setRotation(null);
            }
            
            parallelPortal.setWidth(portal.getWidth() * portal.getScale());
            parallelPortal.setHeight(portal.getHeight() * portal.getScale());
            parallelPortal.setThickness(portal.getThickness() * portal.getScale());
            
            PortalManipulation.copyAdditionalProperties(parallelPortal, portal, false);
            
            parallelPortal.animation.defaultAnimation.inverseScale = true;
            
            parallelPortal.setPortalShape(portal.getPortalShape().cloneIfNecessary());
            
            if (sync) {
                parallelPortal.reloadAndSyncToClientNextTick();
            }
        }
    }
    
    // f1's reverse is t1
    public static void initializeClusterBind(
        Portal f1, Portal f2,
        Portal t1, Portal t2
    ) {
        get(f1).bindCluster = true;
        get(f2).bindCluster = true;
        get(t1).bindCluster = true;
        get(t2).bindCluster = true;
        
        get(f1).flippedPortalId = f2.getUUID();
        get(f2).flippedPortalId = f1.getUUID();
        
        get(t1).flippedPortalId = t2.getUUID();
        get(t2).flippedPortalId = t1.getUUID();
        
        get(f1).reversePortalId = t1.getUUID();
        get(t1).reversePortalId = f1.getUUID();
        
        get(f2).reversePortalId = t2.getUUID();
        get(t2).reversePortalId = f2.getUUID();
        
        get(f1).parallelPortalId = t2.getUUID();
        get(t2).parallelPortalId = f1.getUUID();
        
        get(f2).parallelPortalId = t1.getUUID();
        get(t1).parallelPortalId = f2.getUUID();
        
    }
    
    public static void forClusterPortals(Portal portal, Consumer<Portal> func) {
        func.accept(portal);
        
        forConnectedPortals(portal, func);
    }
    
    public static void forConnectedPortals(Portal portal, Consumer<Portal> func) {
        PortalExtension extension = PortalExtension.get(portal);
        if (extension.flippedPortal != null) {
            func.accept(extension.flippedPortal);
        }
        if (extension.reversePortal != null) {
            func.accept(extension.reversePortal);
        }
        if (extension.parallelPortal != null) {
            func.accept(extension.parallelPortal);
        }
    }
    
    public static void forEachClusterPortal(
        Portal portal,
        Consumer<Portal> forThis,
        Consumer<Portal> forFlipped,
        Consumer<Portal> forReverse,
        Consumer<Portal> forParallel
    ) {
        forThis.accept(portal);
        PortalExtension extension = PortalExtension.get(portal);
        if (extension.flippedPortal != null) {
            forFlipped.accept(extension.flippedPortal);
        }
        if (extension.reversePortal != null) {
            forReverse.accept(extension.reversePortal);
        }
        if (extension.parallelPortal != null) {
            forParallel.accept(extension.parallelPortal);
        }
    }
    
}
