package me.nettee.pancake.core;

import org.apache.log4j.Logger;
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
	
	private static Logger logger = Logger.getLogger(Engine.class);
	
	public void execute(String sql) {
		System.out.println("executing statement \"" + sql + "\"");
		
		try {
			Statement statement = CCJSqlParserUtil.parse(sql);
			ExecuteVisitor executor = new ExecuteVisitor();
			statement.accept(executor);
			executor.execute();
			
			
		} catch (JSQLParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
