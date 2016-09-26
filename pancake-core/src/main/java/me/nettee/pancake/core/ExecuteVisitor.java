package me.nettee.pancake.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import net.sf.jsqlparser.schema.Table;
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
import net.sf.jsqlparser.statement.select.FromItemVisitor;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.ValuesList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Execute SQL statement.
 * <p>
 * Support only Select statement currently.
 * 
 * @author nettee
 *
 */
public class ExecuteVisitor implements StatementVisitor, SelectVisitor, FromItemVisitor {

	private static Logger logger = Logger.getLogger(ExecuteVisitor.class);
	
	List<String> tables = new ArrayList<String>();
	
	public List<String> getTables() {
		return tables;
	}
	
	public void execute() {
		System.out.println("tables:");
		for (String table : tables) {
			System.out.println(table);
		}
		
		
	}

	// Override methods in StatementVisitor
	
	public void visit(Select statement) {
		logger.info(statement.getClass().getSimpleName());
//		List<WithItem> withItemsList = statement.getWithItemsList();
//		if (withItemsList != null) {
//			for (WithItem withItem : withItemsList) {
//				withItem.accept(this);
//			}
//		}
		SelectBody selectBody = statement.getSelectBody();
		selectBody.accept(this);
	}

	public void visit(Delete statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(Update statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(Insert statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(Replace statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(Drop statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(Truncate statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(CreateIndex statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(CreateTable statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(CreateView statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(AlterView statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(Alter statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(Statements statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(Execute statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(SetStatement statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	public void visit(Merge statement) {
		logger.info(statement.getClass().getSimpleName());
	}

	// Override methods in FromItemVisitor
	
	public void visit(Table table) {
		tables.add(table.getName());
	}

	public void visit(SubSelect subSelect) {
		
	}

	public void visit(SubJoin subjoin) {
		
	}

	public void visit(LateralSubSelect lateralSubSelect) {
		
	}

	public void visit(ValuesList valuesList) {
		
	}

	public void visit(TableFunction tableFunction) {
		
	}

	// Override methods in SelectVisitor
	
	public void visit(PlainSelect plainSelect) {
		logger.info(plainSelect.getClass().getSimpleName());
		plainSelect.getFromItem().accept(this);
	}

	public void visit(SetOperationList setOpList) {
		// TODO Auto-generated method stub
		
	}

	public void visit(WithItem withItem) {
		// TODO Auto-generated method stub
		
	}

}
