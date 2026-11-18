package xyz.essencexc.astra.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compile-time checks for Astra option annotations in Java modules. Reports the same mistakes
 * Astra would otherwise only find at game start: wrong field types, final fields, impossible
 * slider ranges, int dropdowns without options, @DependsOn on a missing or non-boolean option,
 * @Button methods with parameters, and more than one option annotation on a field.
 *
 * Enable it in your mod's build: {@code annotationProcessor("xyz.essencexc:astraconfig:<version>")}.
 * Kotlin sources aren't checked (Astra still validates them at startup).
 */
@SupportedAnnotationTypes("xyz.essencexc.astra.api.annotations.*")
public final class AstraProcessor extends AbstractProcessor {
    private static final String PKG = "xyz.essencexc.astra.api.annotations.";
    private static final Set<String> OPTION_ANNOTATIONS = Set.of(
            "Switch", "Checkbox", "MasterToggle", "Slider", "RangeSlider", "Number", "Dropdown", "MultiSelect",
            "ItemList", "Text", "Color", "Keybind", "Info", "Hud", "Position");

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        Set<Element> seen = new HashSet<>();
        for (TypeElement annotation : annotations) {
            for (Element e : round.getElementsAnnotatedWith(annotation)) {
                if (!seen.add(e)) continue;
                if (e.getKind() == ElementKind.FIELD) checkField((VariableElement) e);
                else if (e.getKind() == ElementKind.METHOD) checkMethod((ExecutableElement) e);
            }
        }
        return false; // let other processors see them too
    }

    // ---- fields -------------------------------------------------------------------------------

    private void checkField(VariableElement field) {
        List<AnnotationMirror> options = new ArrayList<>();
        for (AnnotationMirror m : field.getAnnotationMirrors()) {
            String name = name(m);
            if (name.startsWith(PKG) && OPTION_ANNOTATIONS.contains(name.substring(PKG.length()))) options.add(m);
        }
        if (options.isEmpty()) return;
        if (options.size() > 1) {
            error(field, "has more than one option annotation; use exactly one.");
            return;
        }
        AnnotationMirror option = options.get(0);
        String kind = name(option).substring(PKG.length());
        TypeMirror type = field.asType();
        Map<? extends ExecutableElement, ? extends AnnotationValue> values = processingEnv.getElementUtils().getElementValuesWithDefaults(option);

        if (field.getModifiers().contains(Modifier.FINAL) && !kind.equals("Hud")) {
            error(field, "is final. Option fields must be writable: remove 'final'.");
        }
        Element owner = field.getEnclosingElement();
        if (!field.getModifiers().contains(Modifier.STATIC) && isSectionClass(owner)) {
            error(field, "is in a @Section class, so it must be static.");
        }

        switch (kind) {
            case "Switch", "Checkbox", "MasterToggle" -> expect(field, type, kind, isBoolean(type), "boolean");
            case "Slider", "Number" -> {
                expect(field, type, kind, isNumber(type), "int, float or double");
                if (kind.equals("Slider")) {
                    checkRange(field, values);
                    if (bool(values, "logarithmic") && num(values, "min") <= 0) error(field, "@Slider is logarithmic, so min must be above 0.");
                } else if (num(values, "min") >= num(values, "max")) {
                    error(field, "@Number min must be below max.");
                }
            }
            case "RangeSlider" -> {
                expect(field, type, kind, isType(type, "xyz.essencexc.astra.api.value.IntRange") || isType(type, "xyz.essencexc.astra.api.value.FloatRange"), "IntRange or FloatRange");
                checkRange(field, values);
            }
            case "Dropdown" -> {
                boolean isEnum = isEnum(type);
                expect(field, type, kind, isEnum || isInt(type), "an enum, or int with options = {...}");
                if (!isEnum && isInt(type) && strings(values, "options").isEmpty()) error(field, "is an int @Dropdown without options. Add options = {\"A\", \"B\"} or use an enum.");
            }
            case "MultiSelect" -> {
                String raw = erasure(type);
                boolean collection = raw.equals("java.util.Set") || raw.equals("java.util.EnumSet") || raw.equals("java.util.List") || raw.equals("java.util.Collection");
                expect(field, type, kind, collection, "a Set, EnumSet or List");
                TypeMirror element = typeArgument(type);
                if (collection && (element == null || !isEnum(element)) && strings(values, "options").isEmpty()) {
                    error(field, "@MultiSelect holds strings but has no options. Add options = {\"A\", \"B\"} or use an enum element type.");
                }
            }
            case "ItemList" -> {
                TypeMirror element = typeArgument(type);
                expect(field, type, kind, erasure(type).equals("java.util.List") && element != null && isType(element, "java.lang.String"), "List<String>");
            }
            case "Text", "Info" -> expect(field, type, kind, isType(type, "java.lang.String"), "String");
            case "Color" -> expect(field, type, kind, isInt(type), "int (ARGB)");
            case "Keybind" -> expect(field, type, kind, isType(type, "xyz.essencexc.astra.api.keybind.AstraKeybind"), "AstraKeybind");
            case "Hud" -> expect(field, type, kind, isSubtype(type, "xyz.essencexc.astra.api.hud.HudElement"), "a HudElement subclass");
            case "Position" -> expect(field, type, kind, isType(type, "net.minecraft.core.BlockPos") || isType(type, "net.minecraft.world.phys.Vec3"), "BlockPos or Vec3");
            default -> { }
        }

        // @DependsOn must name a boolean option field in the same class.
        for (AnnotationMirror m : field.getAnnotationMirrors()) {
            if (!name(m).equals(PKG + "DependsOn")) continue;
            Map<? extends ExecutableElement, ? extends AnnotationValue> dv = processingEnv.getElementUtils().getElementValuesWithDefaults(m);
            for (String target : strings(dv, "value")) {
                VariableElement other = findField(owner, target);
                if (other == null) error(field, "@DependsOn(\"" + target + "\") names a field that doesn't exist in " + owner.getSimpleName() + ".");
                else if (!isBoolean(other.asType())) error(field, "@DependsOn(\"" + target + "\") must name a boolean option.");
            }
        }
    }

    private void checkRange(VariableElement field, Map<? extends ExecutableElement, ? extends AnnotationValue> values) {
        if (num(values, "min") >= num(values, "max")) error(field, "min must be below max.");
        if (num(values, "step") <= 0) error(field, "step must be above 0.");
    }

    // ---- methods ------------------------------------------------------------------------------

    private void checkMethod(ExecutableElement method) {
        for (AnnotationMirror m : method.getAnnotationMirrors()) {
            if (!name(m).equals(PKG + "Button")) continue;
            if (!method.getParameters().isEmpty()) error(method, "is a @Button, so it must take no parameters.");
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void expect(Element e, TypeMirror type, String annotation, boolean ok, String what) {
        if (!ok) error(e, "is @" + annotation + " but its type is " + type + "; expected " + what + ".");
    }

    private void error(Element e, String message) {
        String owner = e.getEnclosingElement() != null ? e.getEnclosingElement().getSimpleName() + "." : "";
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Astra: '" + owner + e.getSimpleName() + "' " + message, e);
    }

    private static String name(AnnotationMirror m) {
        return ((TypeElement) m.getAnnotationType().asElement()).getQualifiedName().toString();
    }

    private boolean isSectionClass(Element e) {
        if (e == null || (e.getKind() != ElementKind.CLASS)) return false;
        for (AnnotationMirror m : e.getAnnotationMirrors()) if (name(m).equals(PKG + "Section")) return true;
        return false;
    }

    private static VariableElement findField(Element owner, String name) {
        for (Element e : owner.getEnclosedElements()) {
            if (e.getKind() == ElementKind.FIELD && e.getSimpleName().contentEquals(name)) return (VariableElement) e;
        }
        return null;
    }

    private static boolean isBoolean(TypeMirror t) {
        return t.getKind() == TypeKind.BOOLEAN || t.toString().equals("java.lang.Boolean");
    }

    private static boolean isInt(TypeMirror t) {
        return t.getKind() == TypeKind.INT || t.toString().equals("java.lang.Integer");
    }

    private static boolean isNumber(TypeMirror t) {
        return switch (t.getKind()) {
            case INT, FLOAT, DOUBLE -> true;
            default -> Set.of("java.lang.Integer", "java.lang.Float", "java.lang.Double").contains(t.toString());
        };
    }

    private boolean isEnum(TypeMirror t) {
        Element e = processingEnv.getTypeUtils().asElement(t);
        return e != null && e.getKind() == ElementKind.ENUM;
    }

    private String erasure(TypeMirror t) {
        Element e = processingEnv.getTypeUtils().asElement(processingEnv.getTypeUtils().erasure(t));
        return e instanceof TypeElement te ? te.getQualifiedName().toString() : t.toString();
    }

    private boolean isType(TypeMirror t, String qualified) {
        return t.getKind() == TypeKind.DECLARED && erasure(t).equals(qualified);
    }

    private boolean isSubtype(TypeMirror t, String qualified) {
        TypeElement target = processingEnv.getElementUtils().getTypeElement(qualified);
        if (target == null) return true; // can't tell; Astra checks at startup
        return processingEnv.getTypeUtils().isAssignable(t, processingEnv.getTypeUtils().erasure(target.asType()));
    }

    private static TypeMirror typeArgument(TypeMirror t) {
        if (!(t instanceof DeclaredType d) || d.getTypeArguments().isEmpty()) return null;
        return d.getTypeArguments().get(0);
    }

    private static AnnotationValue value(Map<? extends ExecutableElement, ? extends AnnotationValue> values, String key) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : values.entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(key)) return e.getValue();
        }
        return null;
    }

    private static double num(Map<? extends ExecutableElement, ? extends AnnotationValue> values, String key) {
        AnnotationValue v = value(values, key);
        return v != null && v.getValue() instanceof Number n ? n.doubleValue() : 0;
    }

    private static boolean bool(Map<? extends ExecutableElement, ? extends AnnotationValue> values, String key) {
        AnnotationValue v = value(values, key);
        return v != null && Boolean.TRUE.equals(v.getValue());
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<? extends ExecutableElement, ? extends AnnotationValue> values, String key) {
        AnnotationValue v = value(values, key);
        List<String> out = new ArrayList<>();
        if (v == null || !(v.getValue() instanceof List<?> list)) return out;
        for (Object o : list) out.add(String.valueOf(((AnnotationValue) o).getValue()));
        return out;
    }
}
