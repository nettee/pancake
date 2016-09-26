package me.nettee.pancake.core;

import org.apache.log4j.PropertyConfigurator;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

public class Engine {
	
	static {
		// If maven environment is set, it will get file under src/main/resources dir
		String filepath = Engine.class.getResource("/log4j.properties").getPath();
		PropertyConfigurator.configure(filepath);
	}
	
	public void execute(String sql) {
		System.out.println("executing statement \"" + sql + "\"");
		
		try {
			Statement statement = CCJSqlParserUtil.parse(sql);
			ExecuteVisitor visitor = new ExecuteVisitor();
			// visitor pattern
			statement.accept(visitor);
		} catch (JSQLParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
