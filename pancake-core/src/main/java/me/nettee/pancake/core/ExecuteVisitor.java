package me.nettee.pancake.core;

import org.apache.log4j.Logger;

import net.sf.jsqlparser.statement.SetStatement;
import net.sf.jsqlparser.statement.StatementVisitor;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.AlterView;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.execute.Execute;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.replace.Replace;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

public class ExecuteVisitor implements StatementVisitor {

	private static Logger logger = Logger.getLogger(ExecuteVisitor.class);

	public void visit(Select arg0) {
		logger.info("select");
	}

	public void visit(Delete arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(Update arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(Insert arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(Replace arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(Drop arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(Truncate arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(CreateIndex arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(CreateTable arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(CreateView arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(AlterView arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(Alter arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(Statements arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(Execute arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(SetStatement arg0) {
		// TODO Auto-generated method stub
		
	}

	public void visit(Merge arg0) {
		// TODO Auto-generated method stub
		
	}


}
