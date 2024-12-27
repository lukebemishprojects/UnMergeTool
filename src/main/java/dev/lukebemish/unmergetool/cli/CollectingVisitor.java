package dev.lukebemish.unmergetool.cli;

import dev.lukebemish.unmergetool.common.Distribution;
import dev.lukebemish.unmergetool.common.EnumAnnotation;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

class CollectingVisitor extends ClassVisitor {
    private final Distribution distribution;
    
    private boolean shouldRemove = false;
    private Set<String> removeMethods = new HashSet<>();
    private Set<String> removeFields = new HashSet<>();
    
    public boolean shouldRemove() {
        return this.shouldRemove;
    }
    
    public Set<String> removeMethods() {
        return this.removeMethods;
    }
    
    public Set<String> removeFields() {
        return this.removeFields;
    }
    
    protected CollectingVisitor(@Nullable ClassVisitor classVisitor, Distribution distribution) {
        super(Opcodes.ASM9, classVisitor);
        this.distribution = distribution;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        var superVisitor = super.visitAnnotation(descriptor, visible);
        var runnable = (Runnable) () -> shouldRemove = true;
        for (var annotationType : EnumAnnotation.values()) {
            if (descriptor.equals("L"+annotationType.annotationType.replace('.', '/')+";")) {
                return new WatchingAnnotationVisitor(runnable, superVisitor, annotationType);
            } else if (annotationType.repeatable != null && descriptor.equals("L"+annotationType.repeatable.replace('.', '/')+";")) {
                return new RepeatableAnnotationVisitor(runnable, superVisitor, annotationType);
            }
        }
        return superVisitor;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                var superVisitor = super.visitAnnotation(descriptor, visible);
                var runnable = (Runnable) () -> removeMethods.add(name+descriptor);
                for (var annotationType : EnumAnnotation.values()) {
                    if (descriptor.equals("L"+annotationType.annotationType.replace('.', '/')+";")) {
                        return new WatchingAnnotationVisitor(runnable, superVisitor, annotationType);
                    } else if (annotationType.repeatable != null && descriptor.equals("L"+annotationType.repeatable.replace('.', '/')+";")) {
                        return new RepeatableAnnotationVisitor(runnable, superVisitor, annotationType);
                    }
                }
                return superVisitor;
            }
        };
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return new FieldVisitor(Opcodes.ASM9, super.visitField(access, name, descriptor, signature, value)) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                var superVisitor = super.visitAnnotation(descriptor, visible);
                var runnable = (Runnable) () -> removeFields.add(name+":"+descriptor);
                for (var annotationType : EnumAnnotation.values()) {
                    if (descriptor.equals("L"+annotationType.annotationType.replace('.', '/')+";")) {
                        return new WatchingAnnotationVisitor(runnable, superVisitor, annotationType);
                    } else if (annotationType.repeatable != null && descriptor.equals("L"+annotationType.repeatable.replace('.', '/')+";")) {
                        return new RepeatableAnnotationVisitor(runnable, superVisitor, annotationType);
                    }
                }
                return superVisitor;
            }
        };
    }
    
    private class RepeatableAnnotationVisitor extends AnnotationVisitor {
        private final EnumAnnotation annotationType;
        private final Runnable onRemove;
        
        protected RepeatableAnnotationVisitor(Runnable onRemove, AnnotationVisitor delegate, EnumAnnotation annotationType) {
            super(Opcodes.ASM9, delegate);
            this.annotationType = annotationType;
            this.onRemove = onRemove;
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return new AnnotationVisitor(Opcodes.ASM9, super.visitArray(name)) {
                @Override
                public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                    return new WatchingAnnotationVisitor(onRemove, super.visitAnnotation(name, descriptor), annotationType);
                }
            };
        }
    }

    private class WatchingAnnotationVisitor extends AnnotationVisitor {
        private final Runnable onRemove;
        private final EnumAnnotation annotationType;

        private WatchingAnnotationVisitor(Runnable onRemove, AnnotationVisitor delegate, EnumAnnotation annotationType) {
            super(Opcodes.ASM9, delegate);
            this.onRemove = onRemove;
            this.annotationType = annotationType;
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if ("value".equals(name)) {
                if (!distribution.allowClient && value.equals(annotationType.clientValue) || !distribution.allowServer && value.equals(annotationType.serverValue)) {
                    onRemove.run();
                }
            }
        }
    }
}
