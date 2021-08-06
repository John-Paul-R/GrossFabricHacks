package org.spongepowered.asm.mixin.transformer;

import net.devtech.grossfabrichacks.GrossFabricHacks;
import net.devtech.grossfabrichacks.unsafe.UnsafeUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;

import java.lang.reflect.Field;

public class HackedMixinTransformer extends MixinTransformer {
    public static final Class<MixinTransformer> superclass = MixinTransformer.class;

    public static final HackedMixinTransformer instance;
    public static final MixinProcessor processor;
    public static final Extensions extensions;

    private static final Logger LOGGER = LogManager.getLogger("GrossFabricHacks/HackedMixinTransformer");

    @Override
    public byte[] transformClass(final MixinEnvironment environment, final String name, byte[] classBytes) {
        // raw class patching
        if (GrossFabricHacks.State.transformPreMixinRawClass) {
            classBytes = GrossFabricHacks.State.preMixinRawClassTransformer.transform(name, classBytes);
        }

        // ASM patching
        return this.transform(environment, this.readClass(classBytes), classBytes);
    }

    public byte[] transform(MixinEnvironment environment, ClassNode classNode, byte[] original) {
        final String name = classNode.name;

        // return immediately to reduce jumps and assignments
        if (GrossFabricHacks.State.shouldWrite) {
            if (GrossFabricHacks.State.transformPreMixinAsmClass) {
                GrossFabricHacks.State.preMixinAsmClassTransformer.transform(name, classNode);
            }

            processor.applyMixins(environment, name.replace('/', '.'), classNode);

            if (GrossFabricHacks.State.transformPostMixinAsmClass) {
                GrossFabricHacks.State.postMixinAsmClassTransformer.transform(name, classNode);
            }

            // post mixin raw patching
            if (GrossFabricHacks.State.transformPostMixinRawClass) {
                return GrossFabricHacks.State.postMixinRawClassTransformer.transform(name, this.writeClass(classNode));
            }

            return this.writeClass(classNode);
        }

        if (processor.applyMixins(environment, name.replace('/', '.'), classNode)) {
            return this.writeClass(classNode);
        }

        return original;
    }

    static {
        try {
            final Object mixinTransformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();

            LOGGER.info("MixinTransformer found! " + mixinTransformer);

            // here, we modify the klass pointer in the object to point towards the HackedMixinTransformer class, effectively turning the existing
            // MixinTransformer instance into an instance of HackedMixinTransformer
            UnsafeUtil.unsafeCast(mixinTransformer, "org.spongepowered.asm.mixin.transformer.HackedMixinTransformer");

            LOGGER.info("Unsafe cast mixin transformer success!");

            instance = (HackedMixinTransformer) mixinTransformer;
            Field processorField = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredField("processor");
            processorField.setAccessible(true);
            processor = (MixinProcessor) processorField.get(mixinTransformer);
            Field extensionsField = superclass.getDeclaredField("extensions");
            extensionsField.setAccessible(true);
            extensions = (Extensions) extensionsField.get(mixinTransformer);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
