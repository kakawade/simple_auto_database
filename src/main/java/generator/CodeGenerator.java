package generator;

import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.*;
import org.mybatis.generator.internal.DefaultShellCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CodeGenerator implements CommandLineRunner {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${generator.tables}")
    private String tables;

    @Value("${generator.entity-package:com.example.entity}")
    private String entityPackage;

    @Value("${generator.mapper-package:com.example.mapper}")
    private String mapperPackage;

    @Value("${generator.repository-package:com.example.repository}")
    private String repositoryPackage;

    @Value("${generator.entity-path:src/main/java}")
    private String entityPath;

    @Value("${generator.mapper-path:src/main/java}")
    private String mapperPath;

    @Value("${generator.mapper-xml-path:src/main/resources/mapper}")
    private String mapperXmlPath;

    @Value("${generator.repository-path:src/main/java}")
    private String repositoryPath;

    @Value("${generator.mapper-xml-package:example}")
    private String mapperXmlPackage;

    @Override
    public void run(String... args) throws Exception {
        List<String> warnings = new ArrayList<>();
        Configuration config = new Configuration();

        Context context = new Context(ModelType.CONDITIONAL);
        context.setId("mysqlContext");
        context.setTargetRuntime("MyBatis3");

        // 配置注释生成器
        CommentGeneratorConfiguration commentGeneratorConfig = new CommentGeneratorConfiguration();
        commentGeneratorConfig.setConfigurationType(DatabaseCommentGenerator.class.getName());
        context.setCommentGeneratorConfiguration(commentGeneratorConfig);

        // 数据库连接配置
        JDBCConnectionConfiguration jdbcConfig = new JDBCConnectionConfiguration();
        jdbcConfig.setDriverClass("com.mysql.cj.jdbc.Driver");
        jdbcConfig.setConnectionURL(url);
        jdbcConfig.setUserId(username);
        jdbcConfig.setPassword(password);
        // 设置可以获取表注释信息
        jdbcConfig.addProperty("useInformationSchema", "true");
        jdbcConfig.addProperty("remarks", "true");
        jdbcConfig.addProperty("nullCatalogMeansCurrent", "true");
        context.setJdbcConnectionConfiguration(jdbcConfig);

        // Java模型生成器配置
        JavaModelGeneratorConfiguration modelConfig = new JavaModelGeneratorConfiguration();
        modelConfig.setTargetPackage(entityPackage);
        modelConfig.setTargetProject(entityPath);
        // 使用驼峰命名
        modelConfig.addProperty("useActualColumnNames", "false");
        modelConfig.addProperty("enableSubPackages", "true");
        modelConfig.addProperty("trimStrings", "true");
        context.setJavaModelGeneratorConfiguration(modelConfig);

        // SQL映射文件生成器配置
        SqlMapGeneratorConfiguration sqlMapConfig = new SqlMapGeneratorConfiguration();
        sqlMapConfig.setTargetPackage(mapperXmlPackage);
        sqlMapConfig.setTargetProject(mapperXmlPath);
        context.setSqlMapGeneratorConfiguration(sqlMapConfig);

        // Mapper接口生成器配置
        JavaClientGeneratorConfiguration clientConfig = new JavaClientGeneratorConfiguration();
        clientConfig.setTargetPackage(mapperPackage);
        clientConfig.setTargetProject(mapperPath);
        clientConfig.setConfigurationType("XMLMAPPER");
        context.setJavaClientGeneratorConfiguration(clientConfig);

        // 添加表配置
        String[] tableNames = tables.split(",");
        for (String tableName : tableNames) {
            TableConfiguration tableConfig = new TableConfiguration(context);
            tableConfig.setTableName(tableName.trim());
            tableConfig.setDomainObjectName(toClassName(tableName.trim()));
            
            // 使用驼峰命名
            tableConfig.setMapperName(toClassName(tableName.trim()) + "Mapper");
            tableConfig.addProperty("useActualColumnNames", "false");
            
            // 生成全字段
            tableConfig.addProperty("ignoreQualifiersAtRuntime", "true");
            tableConfig.addProperty("useColumnIndexes", "false");
            tableConfig.addProperty("useCompoundPropertyNames", "false");
            
            // 启用所有列
            tableConfig.addProperty("allColumns", "true");
            tableConfig.addProperty("ignoreColumnsByRegex", "");
            
            // 只生成基本的CRUD方法
            tableConfig.setSelectByExampleStatementEnabled(false);
            tableConfig.setDeleteByExampleStatementEnabled(false);
            tableConfig.setCountByExampleStatementEnabled(false);
            tableConfig.setUpdateByExampleStatementEnabled(false);
            
            // 添加列重命名规则
            for (ColumnOverride override : getColumnOverrides(tableName)) {
                tableConfig.addColumnOverride(override);
            }
            
            context.addTableConfiguration(tableConfig);
            
            // 生成Repository
            generateRepository(tableName);
        }

        config.addContext(context);

        DefaultShellCallback callback = new DefaultShellCallback(true);
        MyBatisGenerator generator = new MyBatisGenerator(config, callback, warnings);
        generator.generate(null);
    }

    /**
     * 获取列重命名规则
     */
    private List<ColumnOverride> getColumnOverrides(String tableName) {
        List<ColumnOverride> overrides = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, tableName, null);
            
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                ColumnOverride override = new ColumnOverride(columnName);
                override.setJavaProperty(toCamelCase(columnName.toLowerCase()));
                overrides.add(override);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return overrides;
    }

    /**
     * 转换为驼峰命名（首字母小写）
     */
    private String toCamelCase(String name) {
        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = false;
        
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            
            if (c == '_') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    result.append(Character.toUpperCase(c));
                    nextUpperCase = false;
                } else {
                    // 确保首字母小写
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        
        return result.toString();
    }

    /**
     * 转换为类名（首字母大写）
     */
    private String toClassName(String name) {
        String camelCase = toCamelCase(name);
        return Character.toUpperCase(camelCase.charAt(0)) + camelCase.substring(1);
    }

    /**
     * 生成Repository类
     */
    private void generateRepository(String tableName) throws Exception {
        String className = toClassName(tableName);
        
        // 使用配置的路径
        String repositoryFilePath = repositoryPath + "/" + repositoryPackage.replace('.', '/');
        File dir = new File(repositoryFilePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = repositoryFilePath + "/" + className + "Repository.java";
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.println("package " + repositoryPackage + ";");
            writer.println();
            writer.println("import " + entityPackage + "." + className + ";");
            writer.println("import " + mapperPackage + "." + className + "Mapper;");
            writer.println("import org.springframework.beans.factory.annotation.Autowired;");
            writer.println("import org.springframework.stereotype.Repository;");
            writer.println();
            writer.println("/**");
            writer.println(" * " + getTableComment(tableName));
            writer.println(" */");
            writer.println("@Repository");
            writer.println("public class " + className + "Repository {");
            writer.println();
            writer.println("    @Autowired");
            writer.println("    private " + className + "Mapper mapper;");
            writer.println();
            
            // 只生成基本的CRUD方法
            generateBasicMethods(writer, className);
            
            writer.println("}");
        }
    }

    /**
     * 生成基本的CRUD方法
     */
    private void generateBasicMethods(PrintWriter writer, String className) {
        // insert方法
        writer.println("    /**");
        writer.println("     * 新增记录");
        writer.println("     */");
        writer.println("    public int insert(" + className + " record) {");
        writer.println("        return mapper.insert(record);");
        writer.println("    }");
        writer.println();

        // insertSelective方法
        writer.println("    /**");
        writer.println("     * 选择性新增记录");
        writer.println("     */");
        writer.println("    public int insertSelective(" + className + " record) {");
        writer.println("        return mapper.insertSelective(record);");
        writer.println("    }");
        writer.println();

        // getById方法
        writer.println("    /**");
        writer.println("     * 根据主键查询");
        writer.println("     */");
        writer.println("    public " + className + " getById(Long id) {");
        writer.println("        return mapper.selectByPrimaryKey(id);");
        writer.println("    }");
        writer.println();

        // update方法
        writer.println("    /**");
        writer.println("     * 根据主键更新");
        writer.println("     */");
        writer.println("    public int update(" + className + " record) {");
        writer.println("        return mapper.updateByPrimaryKey(record);");
        writer.println("    }");
        writer.println();

        // updateSelective方法
        writer.println("    /**");
        writer.println("     * 根据主键选择性更新");
        writer.println("     */");
        writer.println("    public int updateSelective(" + className + " record) {");
        writer.println("        return mapper.updateByPrimaryKeySelective(record);");
        writer.println("    }");
        writer.println();

        // delete方法
        writer.println("    /**");
        writer.println("     * 根据主键删除");
        writer.println("     */");
        writer.println("    public int delete(Long id) {");
        writer.println("        return mapper.deleteByPrimaryKey(id);");
        writer.println("    }");
        writer.println();
    }

    /**
     * 获取表注释
     */
    private String getTableComment(String tableName) {
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"});
            if (rs.next()) {
                return rs.getString("REMARKS");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tableName;
    }
} 