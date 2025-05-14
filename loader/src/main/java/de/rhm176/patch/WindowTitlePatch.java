package de.rhm176.patch;

import de.rhm176.loader.EquilinoxGameProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link GamePatch} that modifies the game's window title to include
 * the game version and Fabric Loader version.
 * <p>
 * This patch targets the {@code createDisplay} method in the game's {@code DisplayManager} class.
 * It intercepts the call to {@code Display.setTitle(String)} and appends additional version
 * information to the original title string.
 */
@ApiStatus.Internal
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

    /**
     * Constructs a new WindowTitlePatch.
     *
     * @param gameProvider The provider used for game-specific information, such as the game version.
     */
    public WindowTitlePatch(EquilinoxGameProvider gameProvider) {
        this.gameProvider = gameProvider;
    }

    /**
     * Processes the game classes to apply the window title patch.
     * <p>
     * This method is called by the Fabric loader during game startup. It locates the
     * target class ({@value #TARGET_CLASS_INTERNAL_NAME}) and method ({@value #TARGET_METHOD_NAME}),
     * and if found, attempts to transform it using {@link #transformCreateDisplayMethod(MethodNode)}.
     *
     * @param launcher     The current {@link FabricLauncher} instance.
     * @param classSource  A function to retrieve a {@link ClassNode}.
     * @param classEmitter A consumer to accept the modified {@link ClassNode}.
     */
    @Override
    public void process(FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
        ClassNode displayManagerClass = classSource.apply(TARGET_CLASS_INTERNAL_NAME.replace('/', '.'));
        if (displayManagerClass == null) {
            return;
        }

        for (MethodNode methodNode : displayManagerClass.methods) {
            if (TARGET_METHOD_NAME.equals(methodNode.name)) {
                Log.debug(LogCategory.GAME_PATCH, "Applying window title hook to %s::%s",
                        displayManagerClass.name, methodNode.name);
                if (transformCreateDisplayMethod(methodNode)) {
                    classEmitter.accept(displayManagerClass);
                    break;
                }
            }
        }
    }

    /**
     * Transforms the {@code createDisplay} method to modify the window title.
     * <p>
     * This method searches for a specific pattern of bytecode instructions:
     * <ol>
     * <li>An instruction loading the argument {@code 1}.</li>
     * <li>A static call to {@code GameText.getText(1)}.</li>
     * <li>A static call to {@code Display.setTitle(String)}.</li>
     * </ol>
     * If this pattern is found, it inserts additional bytecode before the {@code Display.setTitle} call
     * to concatenate the game version and Fabric Loader version to the original title string.
     *
     * @param methodNode The {@link MethodNode} of the {@code createDisplay} method to be transformed.
     * @return {@code true} if the method was successfully modified, {@code false} otherwise.
     */
    private boolean transformCreateDisplayMethod(MethodNode methodNode) {
        boolean methodModified = false;

        for (AbstractInsnNode currentInsn : methodNode.instructions) {
            if (currentInsn.getOpcode() == Opcodes.INVOKESTATIC && currentInsn instanceof MethodInsnNode setTitleCall) {
                if (DISPLAY_CLASS_INTERNAL_NAME.equals(setTitleCall.owner) &&
                        SET_TITLE_METHOD_NAME.equals(setTitleCall.name) &&
                        SET_TITLE_METHOD_DESCRIPTOR.equals(setTitleCall.desc)) {
                    AbstractInsnNode prevToSetTitle = setTitleCall.getPrevious();
                    if (prevToSetTitle != null && prevToSetTitle.getOpcode() == Opcodes.INVOKESTATIC &&
                            prevToSetTitle instanceof MethodInsnNode getTextCall) {
                        if (GAME_TEXT_CLASS_INTERNAL_NAME.equals(getTextCall.owner) &&
                                GET_TEXT_METHOD_NAME.equals(getTextCall.name) &&
                                GET_TEXT_METHOD_DESCRIPTOR.equals(getTextCall.desc)) {
                            AbstractInsnNode argLoadInsn = getTextCall.getPrevious();
                            if (argLoadInsn != null && isArgOne(argLoadInsn)) {
                                InsnList instructionsToInsert = new InsnList();
                                instructionsToInsert.add(new LdcInsnNode(" " + gameProvider.getRawGameVersion() +
                                        " - Fabric Loader " + FabricLoaderImpl.VERSION));

                                instructionsToInsert.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
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

    /**
     * Checks if the given instruction node represents loading the integer constant {@code 1}.
     * This is used to identify if {@code GameText.getText(1)} is being called.
     *
     * @param argLoadInsn The {@link AbstractInsnNode} to check.
     * @return {@code true} if the instruction loads the integer {@code 1}, {@code false} otherwise.
     */
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