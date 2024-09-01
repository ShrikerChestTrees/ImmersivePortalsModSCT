package qouteall.imm_ptl.core.portal.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.TickRateManager;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.q_misc_util.my_util.LimitedLogger;

/**
 * Sometimes the client time jumps forward and backward. (This even happens in singleplayer).
 * We want to make the time to be stable (don't flow backward or jump too fast)
 * but still close to the time synchronized from server.
 * {@link net.minecraft.client.multiplayer.ClientPacketListener#handleSetTime(ClientboundSetTimePacket)}
 * <br>
 * We have 3 different times:
 * - Server synced time: level.gameTime + partialTicks
 * - Client reference time: referenceTickTime + partialTicks
 * - Stable time: stableTickTime + stablePartialTicks
 * The level.gameTime may jump due to synchronization.
 * Client reference time won't jump and flows stably, but it's far from the server synced time.
 * Stable time should be stable and close to the server synced time plus 1 tick.
 * <br>
 * stableTime = clientReferenceTime + offsetTime
 * <br>
 * The requirements are:
 * - The stable time always flows forward, it doesn't flow backward or stop.
 * - The stable time should approach server synced time in a short period of time, even if the server synced time jumps.
 */
@Environment(EnvType.CLIENT)
public class StableClientTimer {
    // use two numbers to keep precision (maybe one double is enough?)
    public static final class Time {
        final long tickTime;
        final float partialTicks;
        
        Time(long tickTime, float partialTicks) {
            this.tickTime = tickTime;
            this.partialTicks = partialTicks;
        }
        
        Time normalized() {
            long tickTime = this.tickTime;
            float partialTicks = this.partialTicks;
            
            if (partialTicks < 0) {
                int fullTicks = (int) Math.floor(partialTicks);
                partialTicks -= fullTicks;
                tickTime += fullTicks;
            }
            
            if (partialTicks >= 1) {
                int fullTicks = (int) partialTicks;
                partialTicks -= fullTicks;
                tickTime += fullTicks;
            }
            
            return new Time(tickTime, partialTicks);
        }
        
        double subtractedLen(Time another) {
            return (tickTime - another.tickTime) + partialTicks - another.partialTicks;
        }
        
        Time subtracted(Time another) {
            return new Time(
                tickTime - another.tickTime,
                partialTicks - another.partialTicks
            ).normalized();
        }
        
        Time added(Time another) {
            return new Time(
                tickTime + another.tickTime,
                partialTicks + another.partialTicks
            ).normalized();
        }
        
        Time added(float delta) {
            return new Time(tickTime, partialTicks + delta).normalized();
        }
        
        @Override
        public String toString() {
            return "%d+%.3f".formatted(tickTime, partialTicks);
        }
    }
    
    private static final LimitedLogger limitedLogger = new LimitedLogger(20);
    
    private static boolean initialized = false;
    
    // referenceTime + offset = stableTime
    // offset = stableTime - referenceTime
    @Nullable
    private static Time stableTime = null;
    @Nullable
    private static Time offsetTime = null;
    
    private static long referenceTickTime = 0;
    
    private static double timeFlowScale = 1;
    
    @Nullable
    private static double debugOffset;
    
    public static void init() {
        IPCGlobal.CLIENT_CLEANUP_EVENT.register(StableClientTimer::cleanup);
    }
    
    public static long getStableTickTime() {
        if (stableTime == null) {
            return 0;
        }
        return stableTime.tickTime;
    }
    
    public static float getStablePartialTicks() {
        if (stableTime == null) {
            return 0;
        }
        return stableTime.partialTicks;
    }
    
    private static void cleanup() {
        initialized = false;
        stableTime = null;
        offsetTime = null;
    }
    
    private static void reset(long worldGameTime, float partialTicks) {
        timeFlowScale = 1;
        stableTime = new Time(worldGameTime, partialTicks);
        
        Time referenceTime = new Time(referenceTickTime, partialTicks);
        offsetTime = stableTime.subtracted(referenceTime);
    }
    
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        
        TickRateManager tickRateManager = mc.level.tickRateManager();
        
        if (tickRateManager.tickrate() <= 20.0) {
            referenceTickTime += 1;
        }
        else {
            // weirdly, when tick rate is more than 20,
            // seems only server side is accelerated,
            // the client does not accelerate
            // TODO refactor this for fast tick rate
            referenceTickTime += (long) (tickRateManager.tickrate() / 20);
        }
    }
    
    // updated after every tick and before every frame
    public static void update(long worldGameTime, float partialTicks) {
        if (!initialized) {
            initialized = true;
            reset(worldGameTime, partialTicks);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        if (mc.level == null) {
            return;
        }
        if (!mc.level.tickRateManager().runsNormally()) {
            return;
        }
        Validate.notNull(stableTime);
        Validate.notNull(offsetTime);
        
        Time lastStableTime = stableTime;
        
        Time referenceTime = new Time(referenceTickTime, partialTicks);
        Time serverSyncedTime = new Time(worldGameTime, partialTicks);
        Time targetTime = serverSyncedTime;
        
        Time projectedStableTime = referenceTime.added(offsetTime);
        double deltaTickTime = targetTime.subtractedLen(projectedStableTime);
        
        double stableTimeDelta = projectedStableTime.subtractedLen(lastStableTime);
        if (stableTimeDelta < 0) {
            // The reference time may be abnormal when the client stops pausing.
            return;
        }
        
        if (deltaTickTime < 0) {
            // targetTime -- projectedStableTime
            // stable time is ahead the target time
            // we should make stable time to go slower
            
            double targetSubtractLastStable =
                targetTime.subtractedLen(lastStableTime);
            if (targetSubtractLastStable < 0) {
                // targetTime -- lastStableTime -- projectedStableTime
                // target time is even behind the last stable time
                
                if (targetSubtractLastStable < -100) {
                    // it's too far behind and cannot be recovered
                    reset(worldGameTime, partialTicks);
                    limitedLogger.err("Reset the client stable timer because the target time is too far behind");
                }
                else {
                    // exponentially decrease the time flow scale
                    if (timeFlowScale > 1) {
                        timeFlowScale = 1;
                    }
                    timeFlowScale *= 0.9999;
                    stableTime = lastStableTime.added((float) (timeFlowScale * stableTimeDelta));
                    offsetTime = stableTime.subtracted(referenceTime);
                }
            }
            else {
                // lastStableTime -- targetTime -- projectedStableTime
                
                stableTime = targetTime;
                offsetTime = stableTime.subtracted(referenceTime);
                timeFlowScale = 1;
            }
        }
        else {
            // projectedStableTime -- targetTime
            // stable time is behind the target time
            // exponentially increase the time flow scale
            if (timeFlowScale < 1) {
                timeFlowScale = 1;
            }
            timeFlowScale *= 1.0001;
            stableTime = lastStableTime.added((float) (timeFlowScale * stableTimeDelta));
            offsetTime = stableTime.subtracted(referenceTime);
        }
        
        debugOffset = targetTime.subtractedLen(stableTime);
    }
    
    public static String getDebugString() {
        double shownOffset = debugOffset;
        if (Math.abs(shownOffset) < 0.0001) {
            shownOffset = 0;
        }
        return String.format(
            "Stable Timer Offset %.3f", shownOffset
        );
    }
}
