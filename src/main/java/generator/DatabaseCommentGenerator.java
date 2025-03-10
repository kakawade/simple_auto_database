package generator;

import org.mybatis.generator.api.CommentGenerator;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.api.dom.xml.XmlElement;

import java.util.Properties;
import java.util.Set;

public class DatabaseCommentGenerator implements CommentGenerator {
    @Override
    public void addConfigurationProperties(Properties properties) {
    }

    @Override
    public void addFieldComment(Field field, IntrospectedTable introspectedTable,
                              IntrospectedColumn introspectedColumn) {
        if (introspectedColumn.getRemarks() != null && !introspectedColumn.getRemarks().isEmpty()) {
            field.addJavaDocLine("/**");
            field.addJavaDocLine(" * " + introspectedColumn.getRemarks());
            field.addJavaDocLine(" */");
        }
    }

    // ... 其他必要的方法实现为空 ...
} 