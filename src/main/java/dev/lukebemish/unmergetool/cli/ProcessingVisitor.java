package dev.lukebemish.unmergetool.cli;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

class ProcessingVisitor extends ClassVisitor {
    private final Set<String> removeMethods;
    private final Set<String> removeFields;
    
    protected ProcessingVisitor(ClassVisitor classVisitor, CollectingVisitor collectingVisitor) {
        super(Opcodes.ASM9, classVisitor);
        this.removeMethods = collectingVisitor.removeMethods();
        this.removeFields = collectingVisitor.removeFields();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (removeMethods.contains(name+descriptor)) {
            return null;
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (removeFields.contains(name+":"+descriptor)) {
            return null;
        }
        return super.visitField(access, name, descriptor, signature, value);
    }
}
