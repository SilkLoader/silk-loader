/*
 * Copyright 2025 Silk Loader
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.rhm176.silk.loader.patch;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class ModInitPatch extends GamePatch {
    @VisibleForTesting
    static final String TARGET_CLASS_INTERNAL_NAME = "main/MainApp";

    @VisibleForTesting
    static final String TARGET_METHOD_NAME = "main";

    @VisibleForTesting
    static final String TARGET_METHOD_DESCRIPTOR = "([Ljava/lang/String;)V";

    @VisibleForTesting
    static final String PATCH_CLASS_INTERNAL_NAME = ModInitPatch.class.getName().replace('.', '/');

    @VisibleForTesting
    static final String INIT_METHOD_NAME = "init";

    @VisibleForTesting
    static final String BEFORE_TARGET_OWNER_INTERNAL_NAME = "gameManaging/GameManager";

    @VisibleForTesting
    static final String BEFORE_TARGET_METHOD_NAME = "init";

    @VisibleForTesting
    static final String BEFORE_TARGET_METHOD_DESCRIPTOR = "()V";

    private static final String INIT_METHOD_DESCRIPTOR = "(Ljava/lang/Object;)V";

    @Override
    public void process(
            FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
        ClassNode mainAppClass = classSource.apply(TARGET_CLASS_INTERNAL_NAME.replace('/', '.'));
        if (mainAppClass == null) {
            Log.error(LogCategory.GAME_PATCH, "Could not find main class for mod init hook.");
            System.exit(1);
        }

        boolean patched = false;
        for (MethodNode methodNode : mainAppClass.methods) {
            if (TARGET_METHOD_NAME.equals(methodNode.name) && TARGET_METHOD_DESCRIPTOR.equals(methodNode.desc)) {
                Log.debug(
                        LogCategory.GAME_PATCH,
                        "Found target method %s::%s%s. Attempting to apply mod init hook.",
                        mainAppClass.name,
                        methodNode.name,
                        methodNode.desc);
                if (injectModInitCall(mainAppClass, methodNode)) {
                    classEmitter.accept(mainAppClass);
                } else {
                    Log.warn(
                            LogCategory.GAME_PATCH,
                            "Failed to apply mod init hook to %s::%s%s. Injection point not found or failed.",
                            mainAppClass.name,
                            methodNode.name,
                            methodNode.desc);
                }
                return;
            }
        }

        if (!patched) {
            Log.error(
                    LogCategory.GAME_PATCH,
                    "ModInitPatch: Target method %s::%s%s not found in class %s. Patch not applied.",
                    TARGET_METHOD_NAME,
                    TARGET_METHOD_DESCRIPTOR,
                    TARGET_CLASS_INTERNAL_NAME,
                    mainAppClass.name);
        }
    }

    private boolean injectModInitCall(ClassNode classNode, MethodNode methodNode) {
        InsnList newInstructions = new InsnList();

        newInstructions.add(new TypeInsnNode(Opcodes.NEW, classNode.name));
        newInstructions.add(new InsnNode(Opcodes.DUP));
        newInstructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.name, "<init>", "()V", false));

        newInstructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, PATCH_CLASS_INTERNAL_NAME, INIT_METHOD_NAME, INIT_METHOD_DESCRIPTOR, false));

        for (AbstractInsnNode instruction : methodNode.instructions) {
            if (instruction instanceof MethodInsnNode methodCall) {
                if (methodCall.getOpcode() == Opcodes.INVOKESTATIC
                        && BEFORE_TARGET_OWNER_INTERNAL_NAME.equals(methodCall.owner)
                        && BEFORE_TARGET_METHOD_NAME.equals(methodCall.name)
                        && BEFORE_TARGET_METHOD_DESCRIPTOR.equals(methodCall.desc)) {
                    methodNode.instructions.insertBefore(methodCall, newInstructions);
                    return true;
                }
            }
        }

        return false;
    }

    public static void init(Object gameInstance) {
        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;

        loader.loadAccessWideners();
        loader.prepareModInit(FabricLoader.getInstance().getGameDir(), gameInstance);
        loader.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
    }
}
