package com.xobotun.idea;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;

import static com.xobotun.idea.ProjectUtils.currentProject;

public class LogUtils {

    public static void showInfo(String info) {
        showInfo(info, currentProject());
    }

    public static void showInfo(String info, Project project) {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);

        if (statusBar != null) {
            statusBar.setInfo(info);
        } else {
            Messages.showMessageDialog(project, info, "This should be in the progress bar, but you don't have any. Is ever that possible?!", Messages.getInformationIcon());
        }
    }
}
