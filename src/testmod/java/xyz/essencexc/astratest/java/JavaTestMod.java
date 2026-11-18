package xyz.essencexc.astratest.java;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import xyz.essencexc.astra.api.Astra;

import java.lang.invoke.MethodHandles;

public final class JavaTestMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Astra.builder("astratest_java")
             .name("Astra Java Test")
             .logo("astraconfig_testmod:icons/logo.svg")
             .command("astrajava")
             .openKey(InputConstants.KEY_RSHIFT)
             .lookup(MethodHandles.lookup())
             .category("Skills", FishingModule.INSTANCE)
             .build();
    }
}
