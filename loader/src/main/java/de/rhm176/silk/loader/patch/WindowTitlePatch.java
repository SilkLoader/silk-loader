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

import de.rhm176.silk.loader.EquilinoxGameProvider;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class WindowTitlePatch extends GamePatch {
    private final EquilinoxGameProvider gameProvider;

    private static final String TARGET_CLASS_INTERNAL_NAME = "basics/DisplayManager";
    private static final String TARGET_METHOD_NAME = "createDisplay";

    private static final String DISPLAY_CLASS_INTERNAL_NAME = "org/lwjgl/opengl/Display";
    private static final String SET_TITLE_METHOD_NAME = "setTitle";
    private static final String SET_TITLE_METHOD_DESCRIPTOR = "(Ljava/lang/String;)V";

    private static final String GAME_TEXT_CLASS_INTERNAL_NAME = "languages/GameText";
    private static final String GET_TEXT_METHOD_NAME = "getText";
    private static final String GET_TEXT_METHOD_DESCRIPTOR = "(I)Ljava/lang/String;";

    public WindowTitlePatch(EquilinoxGameProvider gameProvider) {
        this.gameProvider = gameProvider;
    }

    @Override
    public void process(
            FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
        ClassNode displayManagerClass = classSource.apply(TARGET_CLASS_INTERNAL_NAME.replace('/', '.'));
        if (displayManagerClass == null) {
            return;
        }

        for (MethodNode methodNode : displayManagerClass.methods) {
            if (TARGET_METHOD_NAME.equals(methodNode.name)) {
                Log.debug(
                        LogCategory.GAME_PATCH,
                        "Applying window title hook to %s::%s",
                        displayManagerClass.name,
                        methodNode.name);
                if (transformCreateDisplayMethod(methodNode)) {
                    classEmitter.accept(displayManagerClass);
                    break;
                }
            }
        }
    }

    private boolean transformCreateDisplayMethod(MethodNode methodNode) {
        boolean methodModified = false;

        for (AbstractInsnNode currentInsn : methodNode.instructions) {
            if (currentInsn.getOpcode() == Opcodes.INVOKESTATIC && currentInsn instanceof MethodInsnNode setTitleCall) {
                if (DISPLAY_CLASS_INTERNAL_NAME.equals(setTitleCall.owner)
                        && SET_TITLE_METHOD_NAME.equals(setTitleCall.name)
                        && SET_TITLE_METHOD_DESCRIPTOR.equals(setTitleCall.desc)) {
                    AbstractInsnNode prevToSetTitle = setTitleCall.getPrevious();
                    if (prevToSetTitle != null
                            && prevToSetTitle.getOpcode() == Opcodes.INVOKESTATIC
                            && prevToSetTitle instanceof MethodInsnNode getTextCall) {
                        if (GAME_TEXT_CLASS_INTERNAL_NAME.equals(getTextCall.owner)
                                && GET_TEXT_METHOD_NAME.equals(getTextCall.name)
                                && GET_TEXT_METHOD_DESCRIPTOR.equals(getTextCall.desc)) {
                            AbstractInsnNode argLoadInsn = getTextCall.getPrevious();
                            if (argLoadInsn != null && isArgOne(argLoadInsn)) {
                                InsnList instructionsToInsert = new InsnList();
                                // tries to get fabric loader version dynamically and fallbacks to version
                                // silk-loader was compiled with
                                instructionsToInsert.add(
                                        new LdcInsnNode(" " + gameProvider.getRawGameVersion() + " - Fabric Loader "
                                                + FabricLoaderImpl.INSTANCE
                                                        .getModContainer("fabricloader")
                                                        .map(ModContainer::getMetadata)
                                                        .map(ModMetadata::getVersion)
                                                        .map(Version::getFriendlyString)
                                                        .orElse(FabricLoaderImpl.VERSION)));

                                instructionsToInsert.add(new MethodInsnNode(
                                        Opcodes.INVOKEVIRTUAL,
                                        "java/lang/String",
                                        "concat",
                                        "(Ljava/lang/String;)Ljava/lang/String;",
                                        false));

                                methodNode.instructions.insertBefore(setTitleCall, instructionsToInsert);
                                methodModified = true;

                                break;
                            }
                        }
                    }
                }
            }
        }
        return methodModified;
    }

    private static boolean isArgOne(AbstractInsnNode argLoadInsn) {
        if (argLoadInsn.getOpcode() == Opcodes.ICONST_1) {
            return true;
        } else if (argLoadInsn instanceof LdcInsnNode ldcNode) {
            Object cst = ldcNode.cst;
            return cst instanceof Integer && (Integer) cst == 1;
        }
        return false;
    }
}
