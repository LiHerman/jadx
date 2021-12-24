package jadx.gui.ui.treenodes;

import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import jadx.api.JadxDecompiler;
import jadx.core.export.ApplicationDetailsParams;
import jadx.core.export.ProjectInfo;
import jadx.core.utils.Utils;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.MainWindow;
import jadx.gui.ui.TabbedPane;
import jadx.gui.ui.panel.ContentPanel;
import jadx.gui.ui.panel.HtmlPanel;
import jadx.gui.utils.UiUtils;

public class InterestNode extends JNode {
	private static final long serialVersionUID = 4295299814582784805L;

	private static final ImageIcon ICON = UiUtils.openSvgIcon("nodes/detailView");

	private final MainWindow mainWindow;

	public InterestNode(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
	}

	@Override
	public String getContent() {
		StringEscapeUtils.Builder builder = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML4);
		try {
			builder.append("<html>");
			builder.append("<body>");
			writeInputSummary(builder);
			builder.append("</body>");
		} catch (Exception e) {
			builder.append("Error build summary: ");
			builder.append("<pre>");
			builder.append(Utils.getStackTrace(e));
			builder.append("</pre>");
		}
		return builder.toString();
	}

	private void writeInputSummary(StringEscapeUtils.Builder builder) throws IOException {
		builder.append("<h3>基本信息</h3>");
		JadxDecompiler jadx = mainWindow.getWrapper().getDecompiler();
		ApplicationDetailsParams applicationParams = ProjectInfo.getProjectInfo(jadx).getApplicationParams();
		builder.append("<ul>");
		builder.escape("apk包名: "+applicationParams.getPackageName()).append("<br/>");
		builder.escape("apk名称: "+applicationParams.getApplicationName()).append("<br/>");
		builder.escape("minSdk: "+applicationParams.getMinSdkVersion()).append("<br/>");
		builder.escape("tragetSdk: "+applicationParams.getTargetSdkVersion()).append("<br/>");
		builder.escape("VersionCode: "+applicationParams.getVersionCode()).append("<br/>");
		builder.escape("VersionName: "+applicationParams.getVersionName()).append("<br/>");
		builder.append("</ul>");

		builder.append("<h3>Permission:红色为危险权限</h3>");
		builder.append("<ul>");
		List<String> list = applicationParams.getDangerousPermissions();
		for(String per:list) {
			builder.append("<li>");
			builder.append("<font color=\"#FF0000\">");
			builder.append(per);
			builder.append("</font>");
			builder.append("</li>");
		}
		List<String> nlist = applicationParams.getNormalPermissions();
		for(String per:nlist) {
			builder.append("<li>");
			builder.append(per);
			builder.append("</li>");
		}
		builder.append("</ul>");
	}

	@Override
	public ContentPanel getContentPanel(TabbedPane tabbedPane) {
		return new HtmlPanel(tabbedPane, this);
	}

	@Override
	public String makeString() {
		return "App基本属性";
	}

	@Override
	public Icon getIcon() {
		return ICON;
	}

	@Override
	public JClass getJParent() {
		return null;
	}
}
