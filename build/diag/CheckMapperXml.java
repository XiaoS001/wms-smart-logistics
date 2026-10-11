import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构审查工具：检查 MyBatis 注解 SQL 的 XML 合法性。
 *
 * <p>背景：{@code @Select/@Update} 等注解若以 {@code <script>} 开头，MyBatis 会按 XML 解析。
 * 因此 SQL 中出现裸的小于号（例如 {@code <=}）会导致应用启动失败，报
 * “元素内容必须由格式正确的字符数据或标记组成”。此外 {@code <if test='...'>} 内部
 * 若因字符串拼接把换行与缩进带入标签，也会触发同一错误。</p>
 *
 * <p>实现说明：这些注解的保留级别是 {@code CLASS}，反射（{@code getAnnotations()}）读不到，
 * 因此本工具直接解析 class 文件的常量池，取出全部 UTF-8 常量，对以
 * {@code <script>} 开头的字符串做 XML 语法校验。</p>
 *
 * <p>用法：{@code java CheckMapperXml <classes 目录>}</p>
 */
public class CheckMapperXml {

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

        int classCount = 0;
        int scriptCount = 0;
        int failed = 0;

        for (File cf : classFiles) {
            List<String> constants = readUtf8Constants(cf);
            boolean any = false;
            for (String sql : constants) {
                if (!sql.trim().startsWith("<script>")) {
                    continue;
                }
                any = true;
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
            if (any) {
                classCount++;
                System.out.println("  ok    " + cf.getName());
            }
        }
        System.out.println();
        System.out.println("mapper classes with <script> SQL: " + classCount
                + ", statements checked: " + scriptCount + ", failed: " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /**
     * 解析 class 文件常量池，返回其中全部 CONSTANT_Utf8 常量的值。
     *
     * <p>只实现到能安全跳过各常量类型所需的程度：常量池项按“1 字节 tag + 负载”排列，
     * 其中 Long 与 Double 占两个槽位。</p>
     */
    private static List<String> readUtf8Constants(File classFile) throws IOException {
        List<String> out = new ArrayList<String>();
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(
                new FileInputStream(classFile)))) {
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) {
                return out;
            }
            in.readUnsignedShort();   // minor
            in.readUnsignedShort();   // major
            int poolCount = in.readUnsignedShort();
            for (int i = 1; i < poolCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1:   // Utf8
                        out.add(in.readUTF());
                        break;
                    case 3:   // Integer
                    case 4:   // Float
                        in.skipBytes(4);
                        break;
                    case 5:   // Long
                    case 6:   // Double
                        in.skipBytes(8);
                        i++;  // 占两个槽位
                        break;
                    case 7:   // Class
                    case 8:   // String
                    case 16:  // MethodType
                    case 19:  // Module
                    case 20:  // Package
                        in.skipBytes(2);
                        break;
                    case 15:  // MethodHandle
                        in.skipBytes(3);
                        break;
                    case 9:   // Fieldref
                    case 10:  // Methodref
                    case 11:  // InterfaceMethodref
                    case 12:  // NameAndType
                    case 17:  // Dynamic
                    case 18:  // InvokeDynamic
                        in.skipBytes(4);
                        break;
                    default:
                        // 未知 tag：无法安全继续解析，直接返回已读到的常量
                        return out;
                }
            }
        }
        return out;
    }
}
