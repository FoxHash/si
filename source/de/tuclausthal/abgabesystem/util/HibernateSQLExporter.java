package de.tuclausthal.abgabesystem.util;

import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.tool.hbm2ddl.SchemaExport;

public class HibernateSQLExporter {
	public static void main(String[] fdf) {
		SchemaExport bla = new SchemaExport(new AnnotationConfiguration().configure());
		bla.setOutputFile("1.sql");
		bla.execute(false, false, false, false);
	}
}