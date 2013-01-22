package com.chriswk.gradle.plugins.jetty9

import org.gradle.api.Project
import org.gradle.api.plugins.WarPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.instanceOf
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

class Jetty9PluginTest {
    private final Project project = ProjectBuilder.builder().build();

    @Test
    public void appliesWarPluginAndAddsConventionToProject() {
        new Jetty9Plugin().apply(project)
        assertTrue(project.getPlugins().hasPlugin(WarPlugin))
        assertThat(project.convention.plugins.jetty9, instanceOf(Jetty9PluginConvention))
    }
    @Test
    public void addsTasksToProject() {
        new Jetty9Plugin().apply(project)

        def task = project.tasks[Jetty9Plugin.JETTY9_RUN]
        assertThat(task, instanceOf(Jetty9Run))
//        assertThat(task, dependsOn(CLASSES_TASK_NAME))
        assertThat(task.httpPort, equalTo(project.httpPort))

        task = project.tasks[Jetty9Plugin.JETTY9_RUN_WAR]
        assertThat(task, instanceOf(Jetty9RunWar))

//        assertThat(task, dependsOn(WarPlugin.WAR_TASK_NAME))
        assertThat(task.httpPort, equalTo(project.httpPort))

        task = project.tasks[Jetty9Plugin.JETTY9_STOP]
        assertThat(task, instanceOf(Jetty9Stop))
        assertThat(task.stopPort, equalTo(project.stopPort))
    }

    @Test
    public void addsMappingToNewJettyTasks() {
        new Jetty9Plugin().apply(project)

        def task = project.tasks.add('customRun', Jetty9Run)
//        assertThat(task, dependsOn(CLASSES_TASK_NAME))
        assertThat(task.httpPort, equalTo(project.httpPort))

        task = project.tasks.add('customWar', Jetty9RunWar)
//        assertThat(task, dependsOn(WarPlugin.WAR_TASK_NAME))
        assertThat(task.httpPort, equalTo(project.httpPort))
    }


}
