package dev.chronolink.v2;

import java.io.InputStream;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;

/** Reads fixed real GT/AE binaries; no fabricated interfaces and no game-startup claim. */
public final class ApiContractTests {
    private static int checks;
    private static void method(String jar,String name,final String method,final String desc)throws Exception{final boolean[] found={false};try(ZipFile z=new ZipFile(jar)){if(z.getEntry(name+".class")==null)throw new AssertionError(name);try(InputStream in=z.getInputStream(z.getEntry(name+".class"))){new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5){@Override public MethodVisitor visitMethod(int access,String n,String d,String sig,String[] ex){if(n.equals(method)&&d.equals(desc))found[0]=true;return null;}},ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}}
        if(!found[0])throw new AssertionError(name+"."+method+desc);checks++;System.out.println("PASS real API "+name+"."+method);}
    public static void main(String[] args)throws Exception{String gt=args[0],ae=args[1];
        method(gt,"gregtech/common/misc/WirelessNetworkManager","addEUToGlobalEnergyMap","(Ljava/util/UUID;J)Z");
        method(gt,"gregtech/common/misc/WirelessNetworkManager","getUserEU","(Ljava/util/UUID;)Ljava/math/BigInteger;");
        method(gt,"gregtech/common/misc/WirelessNetworkManager","strongCheckOrAddUser","(Ljava/util/UUID;)V");
        method(gt,"gregtech/api/interfaces/tileentity/IBasicEnergyContainer","getInputVoltage","()J");
        method(gt,"gregtech/api/interfaces/tileentity/IBasicEnergyContainer","getInputAmperage","()J");
        method(ae,"appeng/me/helpers/AENetworkProxy","onReady","()V");
        method(ae,"appeng/me/helpers/AENetworkProxy","getStorage","()Lappeng/api/networking/storage/IStorageGrid;");
        method(ae,"appeng/api/networking/IGridNode","getPlayerID","()I");
        method(ae,"appeng/api/networking/security/ISecurityGrid","hasPermission","(ILappeng/api/config/SecurityPermissions;)Z");
        method(ae,"appeng/util/item/AEItemStack","create","(Lnet/minecraft/item/ItemStack;)Lappeng/util/item/AEItemStack;");
        // This locked AE build accepts Object, not a FluidStack-specific overload.
        method(ae,"appeng/util/item/AEFluidStack","create","(Ljava/lang/Object;)Lappeng/util/item/AEFluidStack;");
        method(ae,"appeng/api/networking/IGridNode","setPlayerID","(I)V");
        method(ae,"appeng/core/worlddata/IWorldPlayerData","getPlayerID","(Lcom/mojang/authlib/GameProfile;)I");
        System.out.println("RESULT: "+checks+" real upstream API contract checks. Not a Minecraft startup or live AE/GT transaction test.");
    }
}
