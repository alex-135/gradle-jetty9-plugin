package com.chriswk.gradle.plugins.jetty9;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.War;

import java.io.File;
import java.util.concurrent.Callable;


public class Jetty9Plugin implements Plugin<Project> {

    public static final String JETTY9_RUN = "jetty9Run";
    public static final String JETTY9_RUN_WAR = "jetty9RunWar";
    public static final String JETTY9_STOP = "jetty9Stop";

    public static final String RELOAD_AUTOMATIC = "automatic";
    public static final String RELOAD_MANUAL = "manual";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(WarPlugin.class);
        Jetty9PluginConvention jetty9Convention = new Jetty9PluginConvention();
        Convention convention = project.getConvention();
        convention.getPlugins().put("jetty9", jetty9Convention);

        configureMappingRules(project, jetty9Convention);
        configureJetty9Run(project);
       // configureJetty9RunWar(project);
        configureJetty9Stop(project, jetty9Convention);
    }

    private void configureMappingRules(final Project project, final Jetty9PluginConvention jetty9Convention) {
        project.getTasks().withType(AbstractJetty9RunTask.class, new Action<AbstractJetty9RunTask>() {
            public void execute(AbstractJetty9RunTask abstractJetty9RunTask) {
                configureAbstractJetty9Task(project, jetty9Convention, abstractJetty9RunTask);
            }
        });
    }

    private void configureJetty9Run(final Project project) {
        project.getTasks().withType(Jetty9Run.class, new Action<Jetty9Run>() {
            public void execute(Jetty9Run jettyRun) {
                jettyRun.getConventionMapping().map("webXml", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getWebXml(project);
                    }
                });
                jettyRun.getConventionMapping().map("classpath", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getJavaConvention(project).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath();
                    }
                });
                jettyRun.getConventionMapping().map("webAppSourceDirectory", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getWarConvention(project).getWebAppDir();
                    }
                });
            }
        });

        Jetty9Run jetty9Run = project.getTasks().add(JETTY9_RUN, Jetty9Run.class);
        jetty9Run.setDescription("Uses your files as and where they are and deploys them to Jetty 9.");
        jetty9Run.setGroup(WarPlugin.WEB_APP_GROUP);
    }


    private void configureJetty9RunWar(final Project project) {
        project.getTasks().withType(Jetty9RunWar.class, new Action<Jetty9RunWar>() {
            public void execute(Jetty9RunWar jetty9RunWar) {
                jetty9RunWar.dependsOn("war");
                jetty9RunWar.getConventionMapping().map("webApp", new Callable<Object>() {
                    public Object call() throws Exception {
                        return ((War) project.getTasks().getByName(WarPlugin.WAR_TASK_NAME));
                    }
                });
            }
        });
        Jetty9RunWar jetty9RunWar = project.getTasks().add(JETTY9_RUN_WAR, Jetty9RunWar.class);
        jetty9RunWar.setDescription("Assembles the webapp into a war and deploys it to Jetty 9.");
        jetty9RunWar.setGroup(WarPlugin.WEB_APP_GROUP);
    }

    private void configureJetty9Stop(Project project, final Jetty9PluginConvention jettyConvention) {
        Jetty9Stop jettyStop = project.getTasks().add(JETTY9_STOP, Jetty9Stop.class);
        jettyStop.setDescription("Stops Jetty.");
        jettyStop.setGroup(WarPlugin.WEB_APP_GROUP);
        jettyStop.getConventionMapping().map("stopPort", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getStopPort();
            }
        });
        jettyStop.getConventionMapping().map("stopKey", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getStopKey();
            }
        });
    }

    private void configureAbstractJetty9Task(final Project project, final Jetty9PluginConvention jettyConvention, AbstractJetty9RunTask jettyTask) {
        jettyTask.setDaemon(false);
        jettyTask.setReload(RELOAD_AUTOMATIC);
        jettyTask.setScanIntervalSeconds(0);
        jettyTask.getConventionMapping().map("contextPath", new Callable<Object>() {
            public Object call() throws Exception {
                return ((War) project.getTasks().getByName(WarPlugin.WAR_TASK_NAME)).getBaseName();
            }
        });
        jettyTask.getConventionMapping().map("httpPort", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getHttpPort();
            }
        });
        jettyTask.getConventionMapping().map("stopPort", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getStopPort();
            }
        });
        jettyTask.getConventionMapping().map("stopKey", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getStopKey();
            }
        });
    }

    private Object getWebXml(Project project) {
        War war = (War) project.getTasks().getByName(WarPlugin.WAR_TASK_NAME);
        File webXml;
        if (war.getWebXml() != null) {
            webXml = war.getWebXml();
        } else {
            webXml = new File(getWarConvention(project).getWebAppDir(), "WEB-INF/web.xml");
        }
        return webXml;
    }
    private WarPluginConvention getWarConvention(Project project) {
        return project.getConvention().getPlugin(WarPluginConvention.class);
    }

    private JavaPluginConvention getJavaConvention(Project project) {
        return project.getConvention().getPlugin(JavaPluginConvention.class);
    }

}
