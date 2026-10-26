package itmo.is1.requirements;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Требования ЛР2 к сценарию jmeter/is1-concurrency.jmx; сам прогон — против сервера, см. jmeter/README.md. */
class JmeterScenarioTest {

    /** Группа потоков, запросы внутри неё (метод, путь, тело), заголовки Authorization и SQL-запросы JDBC. */
    record Group(String name, boolean simultaneous, List<String> requests, List<String> authorization,
                 List<String> sql) {
        boolean has(String method, String pathPart) {
            return requests.stream().anyMatch(r -> r.startsWith(method + " ") && r.contains(pathPart));
        }
    }

    private static List<Group> groups;

    @BeforeAll
    static void parse() throws Exception {
        File jmx = new File("jmeter/is1-concurrency.jmx");
        assertTrue(jmx.isFile(), "нет файла " + jmx.getAbsolutePath());
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(jmx);
        groups = new ArrayList<>();
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (e.getTagName().endsWith("ThreadGroup")) {
                Element tree = nextElement(e);
                List<String> requests = new ArrayList<>();
                NodeList samplers = tree.getElementsByTagName("HTTPSamplerProxy");
                for (int j = 0; j < samplers.getLength(); j++) {
                    Element s = (Element) samplers.item(j);
                    requests.add(prop(s, "HTTPSampler.method") + " " + prop(s, "HTTPSampler.path")
                            + " " + prop(s, "Argument.value"));
                }
                List<String> authorization = new ArrayList<>();
                NodeList headers = tree.getElementsByTagName("elementProp");
                for (int j = 0; j < headers.getLength(); j++) {
                    Element h = (Element) headers.item(j);
                    if ("Header".equals(h.getAttribute("elementType")) && "Authorization".equals(prop(h, "Header.name"))) {
                        authorization.add(prop(h, "Header.value"));
                    }
                }
                List<String> sql = new ArrayList<>();
                NodeList jdbc = tree.getElementsByTagName("JDBCSampler");
                for (int j = 0; j < jdbc.getLength(); j++) {
                    sql.add(prop((Element) jdbc.item(j), "query"));
                }
                groups.add(new Group(e.getAttribute("testname"),
                        tree.getElementsByTagName("SyncTimer").getLength() > 0, requests, authorization, sql));
            }
        }
        assertFalse(groups.isEmpty(), "в сценарии нет групп потоков");
    }

    private static Element nextElement(Node node) {
        Node next = node.getNextSibling();
        while (!(next instanceof Element)) {
            next = next.getNextSibling();
        }
        return (Element) next;
    }

    private static String prop(Element element, String name) {
        NodeList props = element.getElementsByTagName("stringProp");
        for (int i = 0; i < props.getLength(); i++) {
            Element p = (Element) props.item(i);
            if (name.equals(p.getAttribute("name"))) {
                return p.getTextContent();
            }
        }
        return "";
    }

    @Test
    void coversCreateUpdateDeleteAndImport() {
        assertTrue(groups.stream().anyMatch(g -> g.requests().stream().anyMatch(r -> r.startsWith("POST /is1/api/persons {"))), "нет создания");
        assertTrue(groups.stream().anyMatch(g -> g.has("PUT", "/persons/")), "нет изменения");
        assertTrue(groups.stream().anyMatch(g -> g.has("DELETE", "/persons/")), "нет удаления");
        assertTrue(groups.stream().anyMatch(g -> g.has("POST", "/persons/import")), "нет импорта");
    }

    @Test
    void severalUsersSimultaneouslyUpdateAndDeleteTheSameObject() {
        assertTrue(groups.stream().anyMatch(g -> g.simultaneous()
                        && g.has("PUT", "${__P(delId)}") && g.has("DELETE", "${__P(delId)}")),
                "нет одновременных изменения и удаления одного объекта");
        assertTrue(groups.stream().anyMatch(g -> g.simultaneous() && g.has("PUT", "${__P(updId)}")),
                "нет одновременного изменения одного объекта");
    }

    @Test
    void severalUsersSimultaneouslyCreateTheSameUniqueValue() {
        assertTrue(groups.stream().anyMatch(g -> g.simultaneous() && g.name().contains("Уникальность имени")
                        && g.has("POST", "/persons")),
                "нет одновременного создания объектов с одним именем");
        assertTrue(groups.stream().anyMatch(g -> g.simultaneous() && g.name().contains("Уникальность координат")),
                "нет одновременного создания одинаковых координат");
        assertTrue(groups.stream().anyMatch(g -> g.simultaneous() && g.has("POST", "/persons/import")),
                "нет одновременного импорта с общим уникальным значением");
    }

    private static Group group(String namePart) {
        return groups.stream().filter(g -> g.name().contains(namePart)).findFirst()
                .orElseThrow(() -> new AssertionError("нет группы «" + namePart + "»"));
    }

    @Test
    void setupCreatesTwoAdministratorsThroughTheDatabase() {
        Group setup = groups.get(0);
        assertTrue(setup.has("POST", "/auth/register {\"username\":\"adm1")
                && setup.has("POST", "/auth/register {\"username\":\"adm2"), "не регистрируются два администратора");
        assertTrue(setup.sql().stream().anyMatch(q -> q.contains("SET role = 'ADMIN'")
                && q.contains("adm1") && q.contains("adm2")), "роль ADMIN не назначается: " + setup.sql());
    }

    @Test
    void twoAdministratorsSimultaneouslyUpdateAndDeleteTheSameObject() {
        for (String name : List.of("изменяют один объект", "изменяют и удаляют один объект")) {
            Group g = group(name);
            assertTrue(g.simultaneous(), name + ": потоки должны стартовать одновременно");
            assertTrue(g.authorization().stream().anyMatch(a -> a.contains("adminToken1") && a.contains("adminToken2")),
                    name + ": запросы должны идти от двух администраторов, а не от владельца: " + g.authorization());
        }
    }

    @Test
    void concurrentModificationsCarryTheVersionTheClientSaw() {
        for (Group g : groups) {
            for (String r : g.requests()) {
                if (r.startsWith("PUT ")) {
                    assertTrue(r.contains("\"version\":"), g.name() + ": изменение без версии: " + r);
                }
                if (r.startsWith("DELETE ")) {
                    assertTrue(r.contains("?version="), g.name() + ": удаление без версии: " + r);
                }
            }
        }
    }

    @Test
    void finalStateIsVerifiedAfterConcurrentGroups() {
        Group last = groups.get(groups.size() - 1);
        assertTrue(last.name().contains("Проверка"), "последняя группа должна проверять итоговое состояние");
        assertTrue(last.requests().size() >= 6, "проверок итогового состояния слишком мало: " + last.requests());
    }
}
