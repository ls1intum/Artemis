package de.tum.in.www1.artemis.config;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.google.auto.service.AutoService;

// TODO this is not working yet
@AutoService(Processor.class)
@SupportedAnnotationTypes({ "javax.persistence.MappedSuperclass", "javax.persistence.Entity" })
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class BeanInfoProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = new HashSet<>(roundEnv.getElementsAnnotatedWith(annotation));
            annotatedElements.stream().filter(ele -> ele.getKind().isClass()).map(ele -> (TypeElement) ele).flatMap(ele -> findSupers(ele).stream())
                    .forEach(ele -> generateBeanInfo((TypeElement) ele));
        }
        return false;
    }

    private Set<TypeElement> findSupers(TypeElement element) {
        Set<TypeElement> ret = new HashSet<>();
        if (element == null) {
            return ret;
        }
        ret.add(element);
        ret.addAll(findSupers((TypeElement) processingEnv.getTypeUtils().asElement(element.getSuperclass())));
        element.getInterfaces().forEach(inter -> {
            ret.addAll(findSupers((TypeElement) processingEnv.getTypeUtils().asElement(inter)));
        });
        return ret;
    }

    private void generateBeanInfo(TypeElement typeElement) {
        String typeName = typeElement.getSimpleName().toString();
        String beanInfoFQN = typeElement.getQualifiedName().toString() + "BeanInfo";
        processingEnv.getMessager().printMessage(Kind.WARNING, "Generating " + beanInfoFQN);
        String beanInfoClassName = typeName + "BeanInfo";
        String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
        try {
            JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(beanInfoFQN);
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                out.println("package " + packageName + ";");
                out.println("import java.beans.BeanDescriptor;\n" + "import java.beans.SimpleBeanInfo;");
                out.println("public class " + beanInfoClassName + " extends SimpleBeanInfo {");
                out.println("  public BeanDescriptor getBeanDescriptor() {");
                out.println("       return new BeanDescriptor(" + typeName + ".class, null);");
                out.println("}}");
            }
        }
        catch (FilerException fe) {
            processingEnv.getMessager().printMessage(Kind.WARNING, "Cannot create " + beanInfoFQN + ", maybe it already exists?");
        }
        catch (IOException e) {
            e.printStackTrace();
            processingEnv.getMessager().printMessage(Kind.ERROR, "IO exception");
        }
    }
}
