import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构审查工具：检查 MyBatis 注解 SQL 的两类陷阱。
 *
 * <p><b>陷阱一（会让应用启动就失败）</b>：{@code @Select/@Update} 等注解若以
 * {@code <script>} 开头，MyBatis 会按 XML 解析。此时 SQL 中裸写的小于号
 * （例如 {@code <=}）是非法 XML 字符，报“元素内容必须由格式正确的字符数据或标记组成”。
 * 必须写成 {@code &lt;=}。</p>
 *
 * <p><b>陷阱二（会让接口 500）</b>：反过来，<b>不带</b> {@code <script>} 的普通 SQL 注解
 * 不做 XML 解析，其中的 {@code &lt;} 会被原样发给数据库，直接造成 SQL 语法错误。
 * 这是陷阱一的镜像，两者必须同时检查。</p>
 *
 * <p>实现说明：这些注解的保留级别是 {@code CLASS}，反射（{@code getAnnotations()}）读不到，
 * 因此本工具直接解析 class 文件的常量池。解析时必须<b>严格按 JVMS 常量类型表跳过负载</b>，
 * 任何长度错误都会导致偏移错位、后续常量全部读错（表现为“0 条语句”）。</p>
 *
 * <p>用法：{@code java CheckMapperXml <classes 目录>}</p>
 */
public class CheckMapperXml {

    /** 普通注解中不允许出现的 XML 实体（会被原样发往数据库） */
    private static final String[] XML_ENTITIES = {"&lt;", "&gt;", "&amp;", "&quot;", "&apos;"};

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "D:/DeepSeekXM/XM01/03-源码/backend/target/classes";
        File mapperDir = new File(dir, "com/lijunjie/wms/mapper");
        if (!mapperDir.isDirectory()) {
            System.out.println("mapper directory not found: " + mapperDir.getAbsolutePath());
            System.exit(2);
        }

        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setNamespaceAware(false);
        try {
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception ignored) {
        }
        javax.xml.parsers.DocumentBuilder builder = f.newDocumentBuilder();

        File[] classFiles = mapperDir.listFiles((d, name) -> name.endsWith(".class"));
        if (classFiles == null) {
            System.out.println("no class files found");
            System.exit(2);
        }
        java.util.Arrays.sort(classFiles);

        int scriptCount = 0;
        int plainCount = 0;
        int failed = 0;
        int entityTrap = 0;

        for (File cf : classFiles) {
            List<String> constants;
            try {
                constants = readUtf8Constants(cf);
            } catch (IOException e) {
                System.out.println("  FAIL  " + cf.getName() + " : cannot parse class file: " + e);
                failed++;
                continue;
            }
            for (String sql : constants) {
                String trimmed = sql.trim();
                boolean looksLikeSql = startsWithSqlKeyword(trimmed);
                if (!looksLikeSql) {
                    continue;
                }
                if (!trimmed.startsWith("<script>")) {
                    // 普通注解：不能被 XML 解析，因此不得包含 XML 实体
                    plainCount++;
                    for (String entity : XML_ENTITIES) {
                        if (sql.contains(entity)) {
                            entityTrap++;
                            System.out.println("  FAIL  " + cf.getName()
                                    + "  : plain SQL annotation contains '" + entity
                                    + "', which is NOT decoded outside <script>");
                            int at = sql.indexOf(entity);
                            System.out.println("        context: ..."
                                    + sql.substring(Math.max(0, at - 60), Math.min(sql.length(), at + 40))
                                    + "...");
                            break;
                        }
                    }
                    continue;
                }
                scriptCount++;
                try {
                    builder.parse(new java.io.ByteArrayInputStream(
                            sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                } catch (org.xml.sax.SAXParseException e) {
                    failed++;
                    int col = e.getColumnNumber();
                    System.out.println("  FAIL  " + cf.getName() + "  col=" + col + " : " + e.getMessage());
                    int from = Math.max(0, col - 70);
                    int to = Math.min(sql.length(), col + 30);
                    System.out.println("        context: ..." + sql.substring(from, to) + "...");
                } catch (Exception e) {
                    failed++;
                    System.out.println("  FAIL  " + cf.getName() + " : " + e);
                }
            }
        }

        System.out.println();
        System.out.println("<script> statements checked: " + scriptCount
                + ", plain SQL annotations checked: " + plainCount
                + ", XML errors: " + failed
                + ", entity traps in plain SQL: " + entityTrap);
        System.exit((failed == 0 && entityTrap == 0) ? 0 : 1);
    }

    private static boolean startsWithSqlKeyword(String s) {
        String upper = s.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("<SCRIPT>") || upper.startsWith("SELECT") || upper.startsWith("INSERT")
                || upper.startsWith("UPDATE") || upper.startsWith("DELETE");
    }

    /**
     * 解析 class 文件常量池，返回全部 CONSTANT_Utf8 常量的值。
     *
     * <p>各常量类型的负载长度（JVMS 4.4）：</p>
     * <ul>
     *   <li>1 Utf8: u2 长度 + 字节</li>
     *   <li>3 Integer / 4 Float: 4</li>
     *   <li>5 Long / 6 Double: 8，且占两个常量池槽位</li>
     *   <li>7 Class / 8 String / 16 MethodType / 19 Module / 20 Package: 2</li>
     *   <li>9/10/11 Fieldref/Methodref/InterfaceMethodref, 12 NameAndType,
     *       17 Dynamic, 18 InvokeDynamic: 4</li>
     *   <li>15 MethodHandle: 3</li>
     * </ul>
     */
    private static List<String> readUtf8Constants(File classFile) throws IOException {
        List<String> out = new ArrayList<String>();
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(
                new FileInputStream(classFile)))) {
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) {
                return out;
            }
            in.readUnsignedShort();   // minor_version
            in.readUnsignedShort();   // major_version
            int poolCount = in.readUnsignedShort();
            for (int i = 1; i < poolCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1:   // CONSTANT_Utf8
                        out.add(in.readUTF());
                        break;
                    case 3:   // CONSTANT_Integer
                    case 4:   // CONSTANT_Float
                        in.skipBytes(4);
                        break;
                    case 5:   // CONSTANT_Long
                    case 6:   // CONSTANT_Double
                        in.skipBytes(8);
                        i++;  // 占两个槽位
                        break;
                    case 7:   // CONSTANT_Class
                    case 8:   // CONSTANT_String
                    case 16:  // CONSTANT_MethodType
                    case 19:  // CONSTANT_Module
                    case 20:  // CONSTANT_Package
                        in.skipBytes(2);
                        break;
                    case 9:   // CONSTANT_Fieldref
                    case 10:  // CONSTANT_Methodref
                    case 11:  // CONSTANT_InterfaceMethodref
                    case 12:  // CONSTANT_NameAndType
                    case 17:  // CONSTANT_Dynamic
                    case 18:  // CONSTANT_InvokeDynamic
                        in.skipBytes(4);
                        break;
                    case 15:  // CONSTANT_MethodHandle
                        in.skipBytes(3);
                        break;
                    default:
                        throw new IOException("unknown constant pool tag " + tag
                                + " at index " + i + " - parser must be updated");
                }
            }
        }
        return out;
    }
}
