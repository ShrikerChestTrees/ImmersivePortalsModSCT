package qouteall.imm_ptl.peripheral;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackInfo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@Environment(EnvType.CLIENT)
public class IPOuterClientMisc {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static class OuterConfig {
        public boolean wikiInformed = false;
        public boolean portalHelperInformed = false;
        public boolean lagInformed = false;
        
        // the dimension stack preset has been moved into IPConfig
        // this is still here for deserialization for upgrade
        @Nullable
        public DimStackInfo dimensionStackDefault = null;
        
        public OuterConfig() {}
    }
    
    private static OuterConfig readFromFile() {
        File storageFile = getStorageFile();
        
        if (storageFile.exists()) {
            
            OuterConfig result = null;
            try (FileReader fileReader = new FileReader(storageFile)) {
                result = IPGlobal.gson.fromJson(fileReader, OuterConfig.class);
            } catch (Throwable e) {
                e.printStackTrace();
                return new OuterConfig();
            }
            
            if (result == null) {
                return new OuterConfig();
            }
            
            return result;
        }
        
        return new OuterConfig();
    }
    
    private static File getStorageFile() {
        return new File(Minecraft.getInstance().gameDirectory, "imm_ptl_state.json");
    }
    
    private static void writeToFile(OuterConfig outerConfig) {
        try (FileWriter fileWriter = new FileWriter(getStorageFile())) {
            
            IPGlobal.gson.toJson(outerConfig, fileWriter);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static OuterConfig outerConfig = new OuterConfig();
    
    public static void initClient() {
        outerConfig = readFromFile();
        
        // upgrade dimension stack preset
        DimStackInfo dimStackPreset = outerConfig.dimensionStackDefault;
        if (dimStackPreset != null) {
            try {
                IPConfig config = IPConfig.getConfig();
                config.dimStackPreset = ((JsonObject) IPGlobal.gson.toJsonTree(dimStackPreset));
                config.saveConfigFile();
                outerConfig.dimensionStackDefault = null;
                writeToFile(outerConfig);
                LOGGER.info("Successfully upgraded dimension stack preset");
            }
            catch (Exception e) {
                LOGGER.info("Failed to upgrade dimension stack preset", e);
            }
        }
        
        Portal.CLIENT_PORTAL_SPAWN_EVENT.register(p -> {
            LocalPlayer player = Minecraft.getInstance().player;
            
            if (!outerConfig.wikiInformed) {
                if (player != null && player.isCreative()) {
                    outerConfig.wikiInformed = true;
                    writeToFile(outerConfig);
                    informWithURL(
                        "https://qouteall.fun/immptl/wiki/Portal-Customization",
                        Component.translatable("imm_ptl.inform_wiki")
                    );
                }
            }
            
        });
    }
    
    private static void informWithURL(String link, MutableComponent text) {
        CHelper.printChat(
            text.append(
                McHelper.getLinkText(link)
            )
        );
    }
}
