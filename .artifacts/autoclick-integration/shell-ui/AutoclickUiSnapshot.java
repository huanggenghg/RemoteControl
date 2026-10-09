import android.app.UiAutomation;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;
import android.util.Xml;
import org.xmlpull.v1.XmlSerializer;
import java.io.StringWriter;

public class AutoclickUiSnapshot {
    static void node(XmlSerializer xml, AccessibilityNodeInfo info) throws Exception {
        if (info == null) return;
        xml.startTag(null, "node");
        xml.attribute(null, "text", info.getText() == null ? "" : info.getText().toString());
        xml.attribute(null, "enabled", String.valueOf(info.isEnabled() && info.isVisibleToUser()));
        Rect rect = new Rect(); info.getBoundsInScreen(rect);
        xml.attribute(null, "bounds", rect.toShortString());
        for (int i=0; i<info.getChildCount(); i++) node(xml, info.getChild(i));
        xml.endTag(null, "node");
    }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        HandlerThread thread = new HandlerThread("autoclick-shell-ui"); thread.start();
        UiAutomation ui = null;
        try {
            Class<?> connectionType = Class.forName("android.app.IUiAutomationConnection");
            Object connection = Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
            ui = (UiAutomation) UiAutomation.class.getConstructor(Looper.class, connectionType).newInstance(thread.getLooper(), connection);
            UiAutomation.class.getMethod("connect", int.class).invoke(ui, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            try { ui.waitForIdle(300, 3000); } catch (java.util.concurrent.TimeoutException ignored) { }
            StringWriter writer = new StringWriter(); XmlSerializer xml = Xml.newSerializer(); xml.setOutput(writer);
            xml.startDocument("UTF-8", true); xml.startTag(null, "hierarchy");
            node(xml, ui.getRootInActiveWindow());
            xml.endTag(null, "hierarchy"); xml.endDocument();
            System.out.println(writer.toString());
        } finally {
            if (ui != null) UiAutomation.class.getMethod("disconnect").invoke(ui);
            thread.quitSafely();
        }
    }
}
