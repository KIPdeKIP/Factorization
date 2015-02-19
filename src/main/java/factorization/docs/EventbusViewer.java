package factorization.docs;

import com.google.common.base.Splitter;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.IEventListener;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class EventbusViewer implements IDocGenerator {
    @Override
    public void process(AbstractTypesetter out, String arg) {
        if ("".equals(arg)) arg = null;
        inspectBus(out, MinecraftForge.EVENT_BUS, "Forge Event Bus", arg);
        inspectBus(out, FMLCommonHandler.instance().bus(), "FML Event Bus", arg);
        inspectBus(out, MinecraftForge.ORE_GEN_BUS, "Ore Gen Bus", arg);
        inspectBus(out, MinecraftForge.TERRAIN_GEN_BUS, "Terrain Gen Bus", arg);
    }

    void inspectBus(AbstractTypesetter out, EventBus bus, String busName, String matchEvent) {
        ConcurrentHashMap<Object, ArrayList<IEventListener>> listeners = ReflectionHelper.getPrivateValue(EventBus.class, bus, "listeners");
        if (listeners == null) {
            out.append("Reflection failed!");
            return;
        }
        HashSet<Method> methodsSet = new HashSet<Method>();
        HashSet<Class<?>> eventTypesSet = new HashSet<Class<?>>();
        for (Map.Entry<Object, ArrayList<IEventListener>> entry : listeners.entrySet()) {
            Object eventHandler = entry.getKey();
            ArrayList<IEventListener> eventListeners = entry.getValue();
            for (Method method : eventHandler.getClass().getMethods()) {
                if (method.getAnnotation(SubscribeEvent.class) != null) {
                    methodsSet.add(method);
                    eventTypesSet.add(method.getParameterTypes()[0]);
                }
            }
        }
        ArrayList<Method> methods = new ArrayList(methodsSet);
        Collections.sort(methods, new Comparator<Method>() {
            @Override
            public int compare(Method o1, Method o2) {
                int c = o1.getClass().getCanonicalName().compareTo(o2.getClass().getCanonicalName());
                if (c != 0) return c;
                return o1.getName().compareTo(o2.getName());
            }
        });
        ArrayList<Class<?>> eventTypes = new ArrayList(eventTypesSet);
        Collections.sort(eventTypes, new Comparator<Class<?>>() {
            @Override
            public int compare(Class<?> o1, Class<?> o2) {
                return o1.getCanonicalName().compareTo(o2.getCanonicalName());
            }
        });

        if (matchEvent != null) {
            boolean first = true;
            for (Method m : methodsSet) {
                String eventTypeName = m.getParameterTypes()[0].getCanonicalName();
                if (!matchEvent.equals(eventTypeName)) continue;
                if (first) {
                    out.append("\\newpage");
                    out.append("\\title{" + busName + "}\\nl");
                    outSplit(out, matchEvent, "cgi/eventbus/" + matchEvent);
                    out.append("\n\n");
                    first = false;
                }
                SubscribeEvent a = m.getAnnotation(SubscribeEvent.class);
                if (a.priority() == EventPriority.NORMAL && !a.receiveCanceled()) {
                    out.append("@SubscribeEvent\\nl");
                } else {
                    out.append(a.toString().replace("cpw.mods.fml.common.eventhandler.", "") + "\\nl");
                }
                outSplit(out, m.getDeclaringClass().getCanonicalName() + "." + m.getName(), null);
                out.append("\n\n");
            }
        } else if (!eventTypes.isEmpty()) {
            out.append("\\newpage");
            out.append("\\title{Events: " + busName + "}\n\n");
            for (Class<?> eventType : eventTypes) {
                final String canonicalName = eventType.getCanonicalName();
                final String simpleName;
                if (eventType.isMemberClass()) {
                    Class<?> highest = eventType;
                    while (highest.getEnclosingClass() != null) {
                        highest = highest.getEnclosingClass();
                    }
                    String hc = highest.getCanonicalName();
                    int start = hc.length();
                    start -= highest.getSimpleName().length();
                    String ec = canonicalName;
                    simpleName = ec.substring(start);
                } else {
                    simpleName = eventType.getSimpleName();
                }
                outSplit(out, simpleName, "cgi/eventbus/" + canonicalName);
                out.append("\\nl");
            }
        }
    }

    Splitter split = Splitter.on(Pattern.compile("(?=[.=A-Z])"));

    void outSplit(AbstractTypesetter out, String text, String link) {
        for (String t : split.split(text)) {
            out.emitWord(new TextWord(t, link));
        }
    }
}