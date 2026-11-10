package com.islandride.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.islandride", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ModuleBoundaryArchitectureTest {

    @ArchTest
    public static final ArchRule controllers_should_be_in_api_or_core_packages =
            classes().that().haveSimpleNameEndingWith("Controller")
                    .should().resideInAnyPackage("..api..", "..core..");

    @ArchTest
    public static final ArchRule exceptions_should_reside_in_exception_package =
            classes().that().haveSimpleNameEndingWith("Exception")
                    .should().resideInAPackage("..exception..");

    @ArchTest
    public static final ArchRule configurations_should_reside_in_config_package =
            classes().that().haveSimpleNameEndingWith("Config")
                    .should().resideInAPackage("..config..");
}

