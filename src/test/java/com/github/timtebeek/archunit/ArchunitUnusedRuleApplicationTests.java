package com.github.timtebeek.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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

@AnalyzeClasses(packagesOf = ArchunitUnusedRuleApplication.class, importOptions = {
        ImportOption.DoNotIncludeArchives.class,
        ImportOption.DoNotIncludeJars.class,
        ImportOption.DoNotIncludeTests.class
})
class ArchunitUnusedRuleApplicationTests {

    @ArchTest
    static ArchRule classesShouldNotBeUnused = freeze(classes()
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
            }));

    @ArchTest
    static ArchRule methodsShouldNotBeUnused = freeze(methods()
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
            }));

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

}

// TODO Check method references; Add tests for the rules themselves