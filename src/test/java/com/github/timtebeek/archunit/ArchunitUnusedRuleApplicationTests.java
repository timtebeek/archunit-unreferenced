package com.github.timtebeek.archunit;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
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

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Utils.namesOf;
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

	// Check classes
	private static DescribedPredicate<JavaClass> classHasMethodWithAnnotationThatEndsWith(String suffix) {
		return describe(String.format("has method with annotation that ends with '%s'", suffix),
				clazz -> clazz.getMethods().stream()
						.flatMap(method -> method.getAnnotations().stream())
						.anyMatch(annotation -> annotation.getRawType().getFullName().endsWith(suffix)));
	}

	private static ArchCondition<JavaClass> shouldBeReferencedClass = new ArchCondition<>("not be unreferenced") {
		@Override
		public void check(JavaClass javaClass, ConditionEvents events) {
			Set<JavaAccess<?>> accesses = new HashSet<>(javaClass.getAccessesToSelf());
			accesses.removeAll(javaClass.getAccessesFromSelf());
			if (accesses.isEmpty() && javaClass.getDirectDependenciesToSelf().isEmpty()) {
				events.add(new SimpleConditionEvent(javaClass, false, String.format("%s is unreferenced in %s",
						javaClass.getDescription(), javaClass.getSourceCodeLocation())));
			}
		}
	};

	private static ArchRule classesShouldNotBeUnused = classes()
			.that().areNotMetaAnnotatedWith(org.springframework.context.annotation.Configuration.class)
			.and().areNotMetaAnnotatedWith(org.springframework.stereotype.Controller.class)
			.and(not(classHasMethodWithAnnotationThatEndsWith("Handler")
					.or(classHasMethodWithAnnotationThatEndsWith("Listener"))
					.or(classHasMethodWithAnnotationThatEndsWith("Scheduled"))
					.or(describe("implements interface", clazz -> !clazz.getAllRawInterfaces().isEmpty()))
					.or(describe("extends class", clazz -> 1 < clazz.getAllRawSuperclasses().size()))
					.and(metaAnnotatedWith(Component.class))))
			.should(shouldBeReferencedClass)
			.as("should use all classes")
			.because("unused classes should be removed");

	@ArchTest
	public static ArchRule classesShouldNotBeUnusedFrozen = freeze(classesShouldNotBeUnused);

	// Check methods
	private static Predicate<JavaMethod> hasMatchingNameAndParameters(JavaMethod input) {
		return m -> m.getName().equals(input.getName())
				&& m.getRawParameterTypes().size() == input.getRawParameterTypes().size()
				&& (m.getDescriptor().equals(input.getDescriptor())
						|| namesOf(m.getRawParameterTypes()).containsAll(namesOf(input.getRawParameterTypes())));
	}

	private static DescribedPredicate<JavaMethod> methodHasAnnotationThatEndsWith(String suffix) {
		return describe(String.format("has annotation that ends with '%s'", suffix),
				method -> method.getAnnotations().stream()
						.anyMatch(annotation -> annotation.getRawType().getFullName().endsWith(suffix)));
	}

	private static ArchCondition<JavaMethod> shouldBeReferencedMethod = new ArchCondition<>("not be unreferenced") {
		@Override
		public void check(JavaMethod javaMethod, ConditionEvents events) {
			Set<JavaCodeUnitAccess<?>> accesses = new HashSet<>(javaMethod.getAccessesToSelf());
			accesses.removeAll(javaMethod.getAccessesFromSelf());
			if (accesses.isEmpty()) {
				events.add(new SimpleConditionEvent(javaMethod, false, String.format("%s is unreferenced in %s",
						javaMethod.getDescription(), javaMethod.getSourceCodeLocation())));
			}
		}
	};

	private static ArchRule methodsShouldNotBeUnused = methods()
			.that(describe("are not declared in super type", input -> !input.getOwner().getAllRawSuperclasses().stream()
					.flatMap(c -> c.getMethods().stream()).anyMatch(hasMatchingNameAndParameters(input))))
			.and(describe("are not declared in interface", input -> !input.getOwner().getAllRawInterfaces().stream()
					.flatMap(i -> i.getMethods().stream()).anyMatch(hasMatchingNameAndParameters(input))))
			.and().doNotHaveName("main")
			.and().areNotMetaAnnotatedWith(RequestMapping.class)
			.and(not(methodHasAnnotationThatEndsWith("Handler")
					.or(methodHasAnnotationThatEndsWith("Listener"))
					.or(methodHasAnnotationThatEndsWith("Scheduled"))
					.and(declaredIn(describe("component", clazz -> clazz.isMetaAnnotatedWith(Component.class))))))
			.should(shouldBeReferencedMethod)
			.as("should use all methods")
			.because("unused methods should be removed");

	@ArchTest
	public static ArchRule methodsShouldNotBeUnusedFrozen = freeze(methodsShouldNotBeUnused);

	@Nested
	static class VerifyRulesThemselves {
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
							Architecture Violation [Priority: MEDIUM] - Rule 'should use all classes, because unused classes should be removed' was violated (2 times):
							Class <com.github.timtebeek.archunit.ComponentD> is unreferenced in (ArchunitUnusedRuleApplication.java:0)
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
							Architecture Violation [Priority: MEDIUM] - Rule 'should use all methods, because unused methods should be removed' was violated (1 times):
							Method <com.github.timtebeek.archunit.ComponentD.doSomething(com.github.timtebeek.archunit.ModelD)> is unreferenced in (ArchunitUnusedRuleApplication.java:102)""",
					error.getMessage());
		}
	}
}
