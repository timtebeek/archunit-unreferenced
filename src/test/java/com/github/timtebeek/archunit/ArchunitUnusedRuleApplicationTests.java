package com.github.timtebeek.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.*;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AnalyzeClasses(packagesOf = ArchunitUnusedRuleApplication.class, importOptions = {
        ImportOption.DoNotIncludeArchives.class,
        ImportOption.DoNotIncludeJars.class,
        ImportOption.DoNotIncludeTests.class
})
class ArchunitUnusedRuleApplicationTests {

    static final ArchRule classesShouldNotBeUnused = classes()
            .that().areNotMetaAnnotatedWith(org.springframework.context.annotation.Configuration.class)
            .and().areNotMetaAnnotatedWith(org.springframework.stereotype.Controller.class)
            .and(not(classHasMethodWithAnnotationThatEndsWith("Handler")
                    .or(classHasMethodWithAnnotationThatEndsWith("Listener"))
                    .or(classHasMethodWithAnnotationThatEndsWith("Scheduled"))
                    .and(metaAnnotatedWith(Component.class))))
            .should(new ArchCondition<>("not be unreferenced") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    Set<JavaAccess<?>> accesses = new HashSet<>(javaClass.getAccessesToSelf());
                    accesses.removeAll(javaClass.getAccessesFromSelf());
                    if (accesses.isEmpty() && javaClass.getDirectDependenciesToSelf().isEmpty()) {
                        events.add(new SimpleConditionEvent(javaClass, false, String.format("%s is unreferenced in %s",
                                javaClass.getDescription(), javaClass.getSourceCodeLocation())));
                    }
                }
            });
    @ArchTest
    static ArchRule classesShouldNotBeUnusedFrozen = freeze(classesShouldNotBeUnused);

    static ArchRule methodsShouldNotBeUnused = methods()
            .that().doNotHaveName("equals")
            .and().doNotHaveName("hashCode")
            .and().doNotHaveName("toString")
            .and().doNotHaveName("main")
            .and().areNotMetaAnnotatedWith(RequestMapping.class)
            .and(not(methodHasAnnotationThatEndsWith("Handler")
                    .or(methodHasAnnotationThatEndsWith("Listener"))
                    .or(methodHasAnnotationThatEndsWith("Scheduled"))
                    .and(declaredIn(describe("component", clazz -> clazz.isMetaAnnotatedWith(Component.class))))))
            .should(new ArchCondition<>("not be unreferenced") {
                @Override
                public void check(JavaMethod javaMethod, ConditionEvents events) {
                    Set<JavaMethodCall> accesses = new HashSet<>(javaMethod.getAccessesToSelf());
                    accesses.removeAll(javaMethod.getAccessesFromSelf());
                    if (accesses.isEmpty()) {
                        events.add(new SimpleConditionEvent(javaMethod, false, String.format("%s is unreferenced in %s",
                                javaMethod.getDescription(), javaMethod.getSourceCodeLocation())));
                    }
                }
            });
    @ArchTest
    static ArchRule methodsShouldNotBeUnusedFrozen = freeze(methodsShouldNotBeUnused);

    static DescribedPredicate<JavaClass> classHasMethodWithAnnotationThatEndsWith(String suffix) {
        return describe(String.format("has method with annotation that ends with '%s'", suffix),
                clazz -> clazz.getMethods().stream()
                        .flatMap(method -> method.getAnnotations().stream())
                        .anyMatch(annotation -> annotation.getRawType().getFullName().endsWith(suffix)));
    }

    static DescribedPredicate<JavaMethod> methodHasAnnotationThatEndsWith(String suffix) {
        return describe(String.format("has annotation that ends with '%s'", suffix),
                method -> method.getAnnotations().stream()
                        .anyMatch(annotation -> annotation.getRawType().getFullName().endsWith(suffix)));
    }

    @Nested
    class VerifyRulesThemselves {
        @Test
        void verifyClassesShouldNotBeUnused() {
            JavaClasses javaClasses = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_ARCHIVES)
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackagesOf(ArchunitUnusedRuleApplication.class);
            AssertionError error = assertThrows(AssertionError.class,
                    () -> classesShouldNotBeUnused.check(javaClasses));
            assertEquals(
                    """
                            Architecture Violation [Priority: MEDIUM] - Rule 'classes that are not meta-annotated with @Configuration and are not meta-annotated with @Controller and not has method with annotation that ends with 'Handler' or has method with annotation that ends with 'Listener' or has method with annotation that ends with 'Scheduled' and meta-annotated with @Component should not be unreferenced' was violated (3 times):
                            Class <com.github.timtebeek.archunit.ComponentD> is unreferenced in (ArchunitUnusedRuleApplication.java:0)
                            Class <com.github.timtebeek.archunit.ModelF> is unreferenced in (ArchunitUnusedRuleApplication.java:0)
                            Class <com.github.timtebeek.archunit.PathsE> is unreferenced in (ArchunitUnusedRuleApplication.java:0)""",
                    error.getMessage());
        }

        @Test
        void verifyMethodsShouldNotBeUnused() {
            JavaClasses javaClasses = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_ARCHIVES)
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackagesOf(ArchunitUnusedRuleApplication.class);
            AssertionError error = assertThrows(AssertionError.class,
                    () -> methodsShouldNotBeUnused.check(javaClasses));
            assertEquals(
                    """
                            Architecture Violation [Priority: MEDIUM] - Rule 'methods that do not have name 'equals' and do not have name 'hashCode' and do not have name 'toString' and do not have name 'main' and are not meta-annotated with @RequestMapping and not has annotation that ends with 'Handler' or has annotation that ends with 'Listener' or has annotation that ends with 'Scheduled' and declared in component should not be unreferenced' was violated (2 times):
                            Method <com.github.timtebeek.archunit.ComponentD.doSomething(com.github.timtebeek.archunit.ModelD)> is unreferenced in (ArchunitUnusedRuleApplication.java:102)
                            Method <com.github.timtebeek.archunit.ModelF.toUpper()> is unreferenced in (ArchunitUnusedRuleApplication.java:143)""",
                    error.getMessage());
        }
    }
}
