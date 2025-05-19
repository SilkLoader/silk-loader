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
package de.rhm176.patch;

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

/**
 * A {@link GamePatch} responsible for injecting Fabric Loader mod initialization code
 * into the game's main application class.
 * <p>
 * This patch targets a specific method within the game's main class and inserts
 * bytecode instructions to call the {@link #init(Object)} method of this patch,
 * which then triggers Fabric's mod loading and entrypoint invocation sequence.
 */
public class ModInitPatch extends GamePatch {
    private static final String TARGET_CLASS_INTERNAL_NAME = "main/MainApp";
    private static final String TARGET_METHOD_NAME = "main";
    private static final String TARGET_METHOD_DESCRIPTOR = "([Ljava/lang/String;)V";

    private static final String PATCH_CLASS_INTERNAL_NAME =
            ModInitPatch.class.getName().replace('.', '/');
    private static final String INIT_METHOD_NAME = "init";
    private static final String INIT_METHOD_DESCRIPTOR = "(Ljava/lang/Object;)V";

    private static final String BEFORE_TARGET_OWNER_INTERNAL_NAME = "gameManaging/GameManager";
    private static final String BEFORE_TARGET_METHOD_NAME = "init";
    private static final String BEFORE_TARGET_METHOD_DESCRIPTOR = "()V";

    /**
     * Processes the game classes to apply the patch.
     * <p>
     * This method is called by the Fabric loader during game startup. It locates the
     * target class and method, and if found, injects a call to {@link #init(Object)}.
     *
     * @param launcher    The current {@link FabricLauncher} instance.
     * @param classSource A function to retrieve a {@link ClassNode} by its name.
     * The name should be in dot-separated format (e.g., {@code com.example.MyClass}).
     * @param classEmitter A consumer to accept the modified {@link ClassNode}.
     */
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
                        LogCategory.GAME_PATCH, "Found target method %s::%s%s. Attempting to apply mod init hook.",
                        mainAppClass.name, methodNode.name, methodNode.desc);
                if (injectModInitCall(mainAppClass, methodNode)) {
                    classEmitter.accept(mainAppClass);
                    Log.info(LogCategory.GAME_PATCH, "Successfully applied mod init hook to %s::%s%s.",
                            mainAppClass.name, methodNode.name, methodNode.desc);
                } else {
                    Log.warn(LogCategory.GAME_PATCH, "Failed to apply mod init hook to %s::%s%s. Injection point not found or failed.",
                            mainAppClass.name, methodNode.name, methodNode.desc);
                }
                return;
            }
        }

        if (!patched) {
            Log.error(LogCategory.GAME_PATCH, "ModInitPatch: Target method %s::%s%s not found in class %s. Patch not applied.",
                    TARGET_METHOD_NAME, TARGET_METHOD_DESCRIPTOR, TARGET_CLASS_INTERNAL_NAME, mainAppClass.name);
        }
    }

    /**
     * Injects bytecode at the beginning of the target method to call {@link ModInitPatch#init(Object)}.
     * <p>
     * The injected bytecode effectively does the following:
     * <pre>
     * ModInitPatch.init(new MainApp());
     * </pre>
     *
     * @param classNode  The {@link ClassNode} of the class containing the target method.
     * @param methodNode The {@link MethodNode} of the target method to be patched.
     * @return {@code true} if the injection was successful (instructions were added), {@code false} otherwise.
     */
    private boolean injectModInitCall(ClassNode classNode, MethodNode methodNode) {
        InsnList newInstructions = new InsnList();

        newInstructions.add(new TypeInsnNode(Opcodes.NEW, classNode.name));
        newInstructions.add(new InsnNode(Opcodes.DUP));
        newInstructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.name, "<init>", "()V", false));

        newInstructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, PATCH_CLASS_INTERNAL_NAME, INIT_METHOD_NAME, INIT_METHOD_DESCRIPTOR, false));

        for (AbstractInsnNode instruction : methodNode.instructions) {
            if (instruction instanceof MethodInsnNode methodCall) {
                if (methodCall.getOpcode() == Opcodes.INVOKESTATIC &&
                        BEFORE_TARGET_OWNER_INTERNAL_NAME.equals(methodCall.owner) &&
                        BEFORE_TARGET_METHOD_NAME.equals(methodCall.name) &&
                        BEFORE_TARGET_METHOD_DESCRIPTOR.equals(methodCall.desc)) {
                    methodNode.instructions.insertBefore(methodCall, newInstructions);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * The actual initialization hook called by the patched game code.
     * <p>
     * This method prepares the {@link FabricLoaderImpl} and invokes the main entrypoints
     * for all loaded mods.
     *
     * @param gameInstance The game instance, passed from the injected bytecode.
     */
    public static void init(Object gameInstance) {
        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;

        loader.prepareModInit(FabricLoader.getInstance().getGameDir(), gameInstance);
        loader.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
    }
}
