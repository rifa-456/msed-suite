package io.github.rifa456.msed;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Packages form layers: each one depends only on layers below it. {@code Msed} (the mod's id and
 * version) and the generated {@code proto} classes are usable everywhere.
 */
@AnalyzeClasses(packages = "io.github.rifa456.msed", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final String ROOT = "io.github.rifa456.msed";

    @ArchTest
    static final ArchRule LAYERS = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Client entrypoint")
            .definedBy(belongToAnyOf(MsedClient.class))
            .layer("Mixins")
            .definedBy(ROOT + ".mixin..")
            .layer("Debug")
            .definedBy(ROOT + ".debug..")
            .layer("Recording")
            .definedBy(ROOT + ".recording..")
            .layer("Action")
            .definedBy(ROOT + ".action..")
            .layer("Observation")
            .definedBy(ROOT + ".observation..")
            .layer("Events")
            .definedBy(ROOT + ".events..")
            .layer("Video")
            .definedBy(ROOT + ".video..")
            .layer("Vocabulary")
            .definedBy(ROOT + ".vocabulary..")
            .layer("Episode")
            .definedBy(ROOT + ".episode..")
            .layer("Config")
            .definedBy(ROOT + ".config..")
            .whereLayer("Client entrypoint")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Mixins")
            .mayOnlyBeAccessedByLayers("Client entrypoint", "Debug", "Recording", "Action", "Observation")
            .whereLayer("Debug")
            .mayOnlyBeAccessedByLayers("Client entrypoint", "Mixins")
            .whereLayer("Recording")
            .mayOnlyBeAccessedByLayers("Client entrypoint", "Mixins", "Debug")
            .whereLayer("Action")
            .mayOnlyBeAccessedByLayers("Client entrypoint", "Mixins", "Debug", "Recording")
            .whereLayer("Observation")
            .mayOnlyBeAccessedByLayers("Client entrypoint", "Mixins", "Debug", "Recording", "Action")
            .whereLayer("Events")
            .mayOnlyBeAccessedByLayers("Client entrypoint", "Mixins", "Recording")
            .whereLayer("Video")
            .mayOnlyBeAccessedByLayers("Client entrypoint", "Recording", "Observation")
            .whereLayer("Vocabulary")
            .mayOnlyBeAccessedByLayers("Client entrypoint", "Mixins", "Debug", "Recording", "Action", "Observation")
            .whereLayer("Episode")
            .mayOnlyBeAccessedByLayers("Client entrypoint", "Recording", "Observation", "Vocabulary")
            .whereLayer("Config")
            .mayOnlyBeAccessedByLayers(
                    "Client entrypoint",
                    "Mixins",
                    "Debug",
                    "Recording",
                    "Action",
                    "Observation",
                    "Video",
                    "Vocabulary",
                    "Episode");
}
