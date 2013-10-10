package com.baidu.lbs.aois;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.baidu.lbs.tools.aois.AoisQuery;

public class Aois {
	
	private static Logger logger = Logger.getLogger(Aois.class);
	
	public static void main(String[] args) throws SQLException {
		
		logger.info("start...");
		Properties config = new Properties();
		try {
			config.load(Aois.class.getClassLoader().getResourceAsStream("aois.properties"));
		} catch (IOException e) {
			logger.error("获取配置文件aois.properties错误", e);
			return;
		}
		AoisQuery aoisQuery = null;
		try{
			String host = config.getProperty("host");
	        int port = Integer.parseInt(config.getProperty("port"));
	        aoisQuery = new AoisQuery(host, port);
		}catch(Exception e){
			logger.error("请求商业圈的地址或端口错误 ", e);
			return;
		}
        String driverClass = config.getProperty("jdbc.driverClassName");
        String url = config.getProperty("jdbc.url");
        String username = config.getProperty("jdbc.username");
        String password = config.getProperty("jdbc.password");
        
        if(StringUtils.isBlank(driverClass) || StringUtils.isBlank(url) ||StringUtils.isBlank(username) ||StringUtils.isBlank(password)){
        	logger.error("jdbc配置不能为空");
        	return;
        }
        String selectSql = "select point_x,point_y,uid FROM dc_baidu_basic d,`lbc_audit_newclaim` l  WHERE d.`uid`=l.`poi_id` AND  l.status=1 order by l.audit_time limit ?,?";
        String updateSql = "update dc_baidu_basic set area_name=? where uid=?";
        
        Connection conn=null;
        PreparedStatement selectPS = null;
        PreparedStatement updatePS = null;
    	try {
			Class.forName(driverClass);
			conn = DriverManager.getConnection(url, username, password);
			conn.setAutoCommit(false);
	        selectPS = conn.prepareStatement(selectSql);
	        updatePS = conn.prepareStatement(updateSql);
		} catch (ClassNotFoundException e) {
			logger.error("初始化mysql driver错误", e);
			return;
		} catch (SQLException e) {
			logger.error("初始化数据库连接错误", e);
			return;
		}
        
        int start = Integer.parseInt(config.getProperty("start"));
        int end = Integer.parseInt(config.getProperty("end"));
        
        if(start<0 || end<=0){
        	logger.error("mysql的limit值不能小于或等于0 start:"+start +",end:"+end);
        	return ;
        }
        logger.info("limit 值为 "+start+","+end);
        while(true){
        	int gap = start;
        	ResultSet rs =null;
        	try{
	        	selectPS.setLong(1, start);
	        	selectPS.setLong(2, end);
	        	rs = selectPS.executeQuery();
	        	while(rs.next()){
	        		start++;
	        		String areaName=null;
	        		try{
	        			areaName = aoisQuery.query(rs.getString("point_x"), rs.getString("point_y"));
	        		}catch(Exception e){
	        			logger.error("查询商业圈信息时出现错误", e);
	        			continue;
	        		}
	        		if(StringUtils.isBlank(areaName)){
        				continue;
        			}
	        		updatePS.setString(1, areaName);
	        		updatePS.setBigDecimal(2, rs.getBigDecimal("uid"));
	        		updatePS.addBatch();
	        		updatePS.clearParameters();
	        	}
        	
	        	logger.info("开始更新第"+gap+"条到"+start+"数据的商业圈信息");
        		updatePS.executeBatch();
        		conn.commit();
        	}catch(Throwable e){
				conn.rollback();
        		logger.error("查询或者更新数据时错误,gap:"+gap+",start:"+start,e);
        		//为了继续向下执行
        		start = end + gap;
        	}finally{
				updatePS.clearBatch();
				rs.close();
        	}
        	//如果结果为真，说明已经没有数据了
        	if(start<end+gap){
        		break;
        	}
			selectPS.clearParameters();
        }
        
        try{
	        updatePS.close();
	        selectPS.close();
	        conn.close();
        }catch(Exception e){
        	logger.error("关闭数据库连接错误", e);
        }
        
        logger.info("end...更新到第"+start+"条数据");
	}
}
