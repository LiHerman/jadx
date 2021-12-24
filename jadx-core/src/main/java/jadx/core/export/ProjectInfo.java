package jadx.core.export;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jadx.api.JadxDecompiler;
import jadx.api.ResourceFile;
import jadx.api.ResourceType;
import jadx.core.dex.nodes.RootNode;
import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.core.xmlgen.ResContainer;

/**
 * @author : Administrator
 * @date : 2021/12/21
 * @description : 项目主要信息
 */
public class ProjectInfo {

	private static volatile ProjectInfo projectInfo;

	private final RootNode root;
	private final File projectDir;
	private final File appDir;
	private final File srcOutDir;
	private final File resOutDir;

	protected ApplicationDetailsParams applicationParams;

	public static ProjectInfo getProjectInfo(JadxDecompiler jadxDecompiler) {
		if(projectInfo == null) {
			synchronized (ProjectInfo.class) {
				if (projectInfo == null) {
					List<ResourceFile> resources = jadxDecompiler.getResources();
					File projectDir = jadxDecompiler.getArgs().getOutDir();
					ResourceFile androidManifest = resources.stream()
							.filter(resourceFile -> resourceFile.getType() == ResourceType.MANIFEST)
							.findFirst()
							.orElseThrow(IllegalStateException::new);
					//resContainer.getFileName() = res\values-am\strings.xml
					//resContainer.getName() = res/values-am/strings.xml
					ResContainer strings = resources.stream()
							.filter(resourceFile -> resourceFile.getType() == ResourceType.ARSC)
							.findFirst()
							.orElseThrow(IllegalStateException::new)
							.loadContent()
							.getSubFiles()
							.stream()
							.filter(resContainer -> resContainer.getFileName().contains("values\\strings.xml"))
							.findFirst()
							.orElseThrow(IllegalStateException::new);
					projectInfo = new ProjectInfo(jadxDecompiler.getRoot(),  projectDir, androidManifest, strings);
				}
			}
		}
		return projectInfo;
	}

	public ProjectInfo(RootNode root, File projectDir, ResourceFile androidManifest, ResContainer appStrings) {
		this.root = root;
		this.projectDir = projectDir;
		this.appDir = new File(projectDir, "app");
		this.srcOutDir = new File(appDir, "src/main/java");
		this.resOutDir = new File(appDir, "src/main");
		this.applicationParams = getApplicationParams(
				parseAndroidManifest(androidManifest),
				parseAppStrings(appStrings));
	}

	protected Document parseAppStrings(ResContainer appStrings) {
		String content = appStrings.getText().getCodeStr();
		return parseXml(content);
	}

	protected Document parseAndroidManifest(ResourceFile androidManifest) {
		String content = androidManifest.loadContent().getText().getCodeStr();

		return parseXml(content);
	}

	protected Document parseXml(String xmlContent) {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document document = builder.parse(new InputSource(new StringReader(xmlContent)));
			document.getDocumentElement().normalize();
			return document;
		} catch (Exception e) {
			throw new JadxRuntimeException("Can not parse xml content", e);
		}
	}

	protected ApplicationDetailsParams getApplicationParams(Document androidManifest, Document appStrings) {
		Element manifest = (Element) androidManifest.getElementsByTagName("manifest").item(0);
		Element usesSdk = (Element) androidManifest.getElementsByTagName("uses-sdk").item(0);
		Element application = (Element) androidManifest.getElementsByTagName("application").item(0);

		Integer versionCode = Integer.valueOf(manifest.getAttribute("android:versionCode"));
		String versionName = manifest.getAttribute("android:versionName");
		String packName = manifest.getAttribute("package");
		Integer minSdk = Integer.valueOf(usesSdk.getAttribute("android:minSdkVersion"));
		Integer targetSdk = Integer.valueOf(usesSdk.getAttribute("android:targetSdkVersion"));
		String appName = "UNKNOWN";

		if (application.hasAttribute("android:label")) {
			String appLabelName = application.getAttribute("android:label");
			if (appLabelName.startsWith("@string")) {
				appLabelName = appLabelName.split("/")[1];
				NodeList strings = appStrings.getElementsByTagName("string");

				for (int i = 0; i < strings.getLength(); i++) {
					String stringName = strings.item(i)
							.getAttributes()
							.getNamedItem("name")
							.getNodeValue();

					if (stringName.equals(appLabelName)) {
						appName = strings.item(i).getTextContent();
						break;
					}
				}
			} else {
				appName = appLabelName;
			}
		}
		applicationParams = new ApplicationDetailsParams(appName, minSdk, targetSdk, versionCode, versionName);
		NodeList nodeList = androidManifest.getElementsByTagName("uses-permission");
		for (int i=0;i<nodeList.getLength();i++) {
			Element node = (Element)nodeList.item(i);
			String perMission = node.getAttribute("android:name");
			applicationParams.addPermission(perMission);
		}
		applicationParams.setPackageName(packName);
		return applicationParams;
	}

	public ApplicationDetailsParams getApplicationParams() {
		return this.applicationParams;
	}

}
