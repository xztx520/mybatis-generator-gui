package com.zzg.mybatis.generator.bridge;

import com.jcraft.jsch.Session;
import com.zzg.mybatis.generator.controller.PictureProcessStateController;
import com.zzg.mybatis.generator.model.DatabaseConfig;
import com.zzg.mybatis.generator.model.DbType;
import com.zzg.mybatis.generator.model.GeneratorConfig;
import com.zzg.mybatis.generator.plugins.DbRemarksCommentGenerator;
import com.zzg.mybatis.generator.util.ConfigHelper;
import com.zzg.mybatis.generator.util.DbUtil;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.api.ProgressCallback;
import org.mybatis.generator.api.ShellCallback;
import org.mybatis.generator.config.*;
import org.mybatis.generator.internal.DefaultShellCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The bridge between GUI and the mybatis generator. All the operation to  mybatis generator should proceed through this
 * class
 * <p>
 * Created by Owen on 6/30/16.
 */
public class MybatisGeneratorBridge {

	private static final Logger _LOG = LoggerFactory.getLogger(MybatisGeneratorBridge.class);

    private GeneratorConfig generatorConfig;

    private DatabaseConfig selectedDatabaseConfig;

    private ProgressCallback progressCallback;

    private List<IgnoredColumn> ignoredColumns;

    private List<ColumnOverride> columnOverrides;

    public MybatisGeneratorBridge() {
    }

    public void setGeneratorConfig(GeneratorConfig generatorConfig) {
        this.generatorConfig = generatorConfig;
    }

    public void setDatabaseConfig(DatabaseConfig databaseConfig) {
        this.selectedDatabaseConfig = databaseConfig;
    }

    public void generate() throws Exception {
        Configuration configuration = new Configuration();
        Context context = new Context(ModelType.CONDITIONAL);
        configuration.addContext(context);
		
        context.addProperty("javaFileEncoding", "UTF-8");
        
		String dbType = selectedDatabaseConfig.getDbType();
		String connectorLibPath = ConfigHelper.findConnectorLibPath(dbType);
	    _LOG.info("connectorLibPath: {}", connectorLibPath);
	    configuration.addClasspathEntry(connectorLibPath);
        // Table configuration
        TableConfiguration tableConfig = new TableConfiguration(context);
        tableConfig.setTableName(generatorConfig.getTableName());
        tableConfig.setDomainObjectName(generatorConfig.getDomainObjectName());
        if(!generatorConfig.isUseExample()) {
            tableConfig.setUpdateByExampleStatementEnabled(false);
            tableConfig.setCountByExampleStatementEnabled(false);
            tableConfig.setDeleteByExampleStatementEnabled(false);
            tableConfig.setSelectByExampleStatementEnabled(false);
        }

		context.addProperty("autoDelimitKeywords", "true");
		if (DbType.MySQL.name().equals(dbType) || DbType.MySQL_8.name().equals(dbType)) {
			tableConfig.setSchema(selectedDatabaseConfig.getSchema());
			// ??????beginningDelimiter???endingDelimiter????????????????????????(")??????Mysql????????????????????????????????????????????????????????????`
			context.addProperty("beginningDelimiter", "`");
			context.addProperty("endingDelimiter", "`");
		} else {
            tableConfig.setCatalog(selectedDatabaseConfig.getSchema());
	    }
        if (generatorConfig.isUseSchemaPrefix()) {
            if (DbType.MySQL.name().equals(dbType) || DbType.MySQL_8.name().equals(dbType)) {
                tableConfig.setSchema(selectedDatabaseConfig.getSchema());
            } else if (DbType.Oracle.name().equals(dbType)) {
                //Oracle???schema???????????????????????????????????????dba???????????????????????????schema???????????????????????????????????????????????????????????????mapper???????????????
                tableConfig.setSchema(selectedDatabaseConfig.getUsername());
            } else {
                tableConfig.setCatalog(selectedDatabaseConfig.getSchema());
            }
        }
        // ?????? postgresql ????????????
		if (DbType.PostgreSQL.name().equals(dbType)) {
            tableConfig.setDelimitIdentifiers(true);
        }

        //??????GeneratedKey????????????
		if (StringUtils.isNotEmpty(generatorConfig.getGenerateKeys())) {
            String dbType2 = dbType;
            if (DbType.MySQL.name().equals(dbType2) || DbType.MySQL_8.name().equals(dbType)) {
                dbType2 = "JDBC";
                //dbType???JDBC?????????????????????useGeneratedKeys??????Mybatis?????????Jdbc3KeyGenerator,
                //?????????KeyGenerator??????????????????????????????INSERT ??????????????????resultSet???????????? ?????????????????????
                //?????????????????????????????????????????????????????????
                //???????????????RDS + ??????????????????
                //??????????????????
                //?????????SelectKey??????Mybatis?????????SelectKeyGenerator???INSERT??????????????????????????????????????????????????????
                //????????????????????????????????????????????????????????????????????????
            }
			tableConfig.setGeneratedKey(new GeneratedKey(generatorConfig.getGenerateKeys(), dbType2, true, null));
		}

        if (generatorConfig.getMapperName() != null) {
            tableConfig.setMapperName(generatorConfig.getMapperName());
        }
        // add ignore columns
        if (ignoredColumns != null) {
            ignoredColumns.forEach(tableConfig::addIgnoredColumn);
        }
        if (columnOverrides != null) {
            columnOverrides.forEach(tableConfig::addColumnOverride);
        }
        if (generatorConfig.isUseActualColumnNames()) {
			tableConfig.addProperty("useActualColumnNames", "true");
        }

		if(generatorConfig.isUseTableNameAlias()){
            tableConfig.setAlias(generatorConfig.getTableName());
        }

        JDBCConnectionConfiguration jdbcConfig = new JDBCConnectionConfiguration();
        if (DbType.MySQL.name().equals(dbType) || DbType.MySQL_8.name().equals(dbType)) {
	        jdbcConfig.addProperty("nullCatalogMeansCurrent", "true");
	        // useInformationSchema?????????????????????????????????????????????????????????????????????
	        jdbcConfig.addProperty("useInformationSchema", "true");
        }
        jdbcConfig.setDriverClass(DbType.valueOf(dbType).getDriverClass());
        jdbcConfig.setConnectionURL(DbUtil.getConnectionUrlWithSchema(selectedDatabaseConfig));
        jdbcConfig.setUserId(selectedDatabaseConfig.getUsername());
        jdbcConfig.setPassword(selectedDatabaseConfig.getPassword());
        if(DbType.Oracle.name().equals(dbType)){
            jdbcConfig.getProperties().setProperty("remarksReporting", "true");
        }
        // java model
        JavaModelGeneratorConfiguration modelConfig = new JavaModelGeneratorConfiguration();
        modelConfig.setTargetPackage(generatorConfig.getModelPackage());
        modelConfig.setTargetProject(generatorConfig.getProjectFolder() + "/" + generatorConfig.getModelPackageTargetFolder());
        // Mapper configuration
        SqlMapGeneratorConfiguration mapperConfig = new SqlMapGeneratorConfiguration();
        mapperConfig.setTargetPackage(generatorConfig.getMappingXMLPackage());
        mapperConfig.setTargetProject(generatorConfig.getProjectFolder() + "/" + generatorConfig.getMappingXMLTargetFolder());
        // DAO
        JavaClientGeneratorConfiguration daoConfig = new JavaClientGeneratorConfiguration();
        daoConfig.setConfigurationType("XMLMAPPER");
        daoConfig.setTargetPackage(generatorConfig.getDaoPackage());
        daoConfig.setTargetProject(generatorConfig.getProjectFolder() + "/" + generatorConfig.getDaoTargetFolder());


        context.setId("myid");
        context.addTableConfiguration(tableConfig);
        context.setJdbcConnectionConfiguration(jdbcConfig);
        context.setJavaModelGeneratorConfiguration(modelConfig);
        context.setSqlMapGeneratorConfiguration(mapperConfig);
        context.setJavaClientGeneratorConfiguration(daoConfig);
        // Comment
        CommentGeneratorConfiguration commentConfig = new CommentGeneratorConfiguration();
        commentConfig.setConfigurationType(DbRemarksCommentGenerator.class.getName());
        if (generatorConfig.isComment()) {
            commentConfig.addProperty("columnRemarks", "true");
        }
        if (generatorConfig.isAnnotation()) {
            commentConfig.addProperty("annotations", "true");
        }
        context.setCommentGeneratorConfiguration(commentConfig);
        // set java file encoding
        context.addProperty(PropertyRegistry.CONTEXT_JAVA_FILE_ENCODING, generatorConfig.getEncoding());

        //?????????????????????
        PluginConfiguration serializablePluginConfiguration = new PluginConfiguration();
        serializablePluginConfiguration.addProperty("type", "org.mybatis.generator.plugins.SerializablePlugin");
        serializablePluginConfiguration.setConfigurationType("org.mybatis.generator.plugins.SerializablePlugin");
        context.addPluginConfiguration(serializablePluginConfiguration);

        // Lombok ??????
        if (generatorConfig.isUseLombokPlugin()) {
            PluginConfiguration pluginConfiguration = new PluginConfiguration();
            pluginConfiguration.addProperty("type", "com.softwareloop.mybatis.generator.plugins.LombokPlugin");
            pluginConfiguration.setConfigurationType("com.softwareloop.mybatis.generator.plugins.LombokPlugin");
            context.addPluginConfiguration(pluginConfiguration);
        }
        // toString, hashCode, equals??????
        else if (generatorConfig.isNeedToStringHashcodeEquals()) {
            PluginConfiguration pluginConfiguration1 = new PluginConfiguration();
            pluginConfiguration1.addProperty("type", "org.mybatis.generator.plugins.EqualsHashCodePlugin");
            pluginConfiguration1.setConfigurationType("org.mybatis.generator.plugins.EqualsHashCodePlugin");
            context.addPluginConfiguration(pluginConfiguration1);
            PluginConfiguration pluginConfiguration2 = new PluginConfiguration();
            pluginConfiguration2.addProperty("type", "org.mybatis.generator.plugins.ToStringPlugin");
            pluginConfiguration2.setConfigurationType("org.mybatis.generator.plugins.ToStringPlugin");
            context.addPluginConfiguration(pluginConfiguration2);
        }
        // limit/offset??????
        if (generatorConfig.isOffsetLimit()) {
            if (DbType.MySQL.name().equals(dbType) || DbType.MySQL_8.name().equals(dbType)
		            || DbType.PostgreSQL.name().equals(dbType)) {
                PluginConfiguration pluginConfiguration = new PluginConfiguration();
                pluginConfiguration.addProperty("type", "com.zzg.mybatis.generator.plugins.MySQLLimitPlugin");
                pluginConfiguration.setConfigurationType("com.zzg.mybatis.generator.plugins.MySQLLimitPlugin");
                context.addPluginConfiguration(pluginConfiguration);
            }
        }
        //for JSR310
        if (generatorConfig.isJsr310Support()) {
            JavaTypeResolverConfiguration javaTypeResolverConfiguration = new JavaTypeResolverConfiguration();
            javaTypeResolverConfiguration.setConfigurationType("com.zzg.mybatis.generator.plugins.JavaTypeResolverJsr310Impl");
            context.setJavaTypeResolverConfiguration(javaTypeResolverConfiguration);
        }
        //forUpdate ??????
        if(generatorConfig.isNeedForUpdate()) {
            if (DbType.MySQL.name().equals(dbType)
                    || DbType.PostgreSQL.name().equals(dbType)) {
                PluginConfiguration pluginConfiguration = new PluginConfiguration();
                pluginConfiguration.addProperty("type", "com.zzg.mybatis.generator.plugins.MySQLForUpdatePlugin");
                pluginConfiguration.setConfigurationType("com.zzg.mybatis.generator.plugins.MySQLForUpdatePlugin");
                context.addPluginConfiguration(pluginConfiguration);
            }
        }
        //repository ??????
        if(generatorConfig.isAnnotationDAO()) {
            if (DbType.MySQL.name().equals(dbType) || DbType.MySQL_8.name().equals(dbType)
                    || DbType.PostgreSQL.name().equals(dbType)) {
                PluginConfiguration pluginConfiguration = new PluginConfiguration();
                pluginConfiguration.addProperty("type", "com.zzg.mybatis.generator.plugins.RepositoryPlugin");
                pluginConfiguration.setConfigurationType("com.zzg.mybatis.generator.plugins.RepositoryPlugin");
                context.addPluginConfiguration(pluginConfiguration);
            }
        }
        if (generatorConfig.isUseDAOExtendStyle()) {
            if (DbType.MySQL.name().equals(dbType) || DbType.MySQL_8.name().equals(dbType)
                    || DbType.PostgreSQL.name().equals(dbType)) {
                PluginConfiguration pluginConfiguration = new PluginConfiguration();
				pluginConfiguration.addProperty("useExample", String.valueOf(generatorConfig.isUseExample()));
				pluginConfiguration.addProperty("type", "com.zzg.mybatis.generator.plugins.CommonDAOInterfacePlugin");
                pluginConfiguration.setConfigurationType("com.zzg.mybatis.generator.plugins.CommonDAOInterfacePlugin");
                context.addPluginConfiguration(pluginConfiguration);
            }
        }

        context.setTargetRuntime("MyBatis3");

        List<String> warnings = new ArrayList<>();
        Set<String> fullyqualifiedTables = new HashSet<>();
        Set<String> contexts = new HashSet<>();
        ShellCallback shellCallback = new DefaultShellCallback(true); // override=true
        MyBatisGenerator myBatisGenerator = new MyBatisGenerator(configuration, shellCallback, warnings);
        // if overrideXML selected, delete oldXML ang generate new one
		if (generatorConfig.isOverrideXML()) {
			String mappingXMLFilePath = getMappingXMLFilePath(generatorConfig);
			File mappingXMLFile = new File(mappingXMLFilePath);
			if (mappingXMLFile.exists()) {
				mappingXMLFile.delete();
			}
		}
        myBatisGenerator.generate(progressCallback, contexts, fullyqualifiedTables);
    }

    private String getMappingXMLFilePath(GeneratorConfig generatorConfig) {
		StringBuilder sb = new StringBuilder();
		sb.append(generatorConfig.getProjectFolder()).append("/");
		sb.append(generatorConfig.getMappingXMLTargetFolder()).append("/");
		String mappingXMLPackage = generatorConfig.getMappingXMLPackage();
		if (StringUtils.isNotEmpty(mappingXMLPackage)) {
			sb.append(mappingXMLPackage.replace(".", "/")).append("/");
		}
		if (StringUtils.isNotEmpty(generatorConfig.getMapperName())) {
			sb.append(generatorConfig.getMapperName()).append(".xml");
		} else {
			sb.append(generatorConfig.getDomainObjectName()).append("Mapper.xml");
		}

		return sb.toString();
	}

	public void setProgressCallback(ProgressCallback progressCallback) {
        this.progressCallback = progressCallback;
    }

    public void setIgnoredColumns(List<IgnoredColumn> ignoredColumns) {
        this.ignoredColumns = ignoredColumns;
    }

    public void setColumnOverrides(List<ColumnOverride> columnOverrides) {
        this.columnOverrides = columnOverrides;
    }
}
