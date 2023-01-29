package com.nyl.eis.ms.unclaimed.repository;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalOperator;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.nyl.eis.ms.unclaimed.config.DBHelper;
import com.nyl.eis.ms.unclaimed.config.GlobalProperties;
import com.nyl.eis.ms.unclaimed.model.UnClaimedFund;
import com.nyl.eis.ms.unclaimed.model.UnclaimedFunds;

@Repository
public class UnclaimedFundsReposiotryImpl implements UnclaimedFundsRepository {

	@Autowired
	DBHelper dbHelper;

	@Autowired
	GlobalProperties globalProperties;

	private static final Log LOG = LogFactory.getLog(UnclaimedFundsReposiotryImpl.class);

	public List<UnclaimedFunds> findBySearchCriteria(final String lastName, final String city, final String state,
			final String zip) throws Exception {

		ScanRequest scanRequest = new ScanRequest(globalProperties.getTable());
		int paramCount = 0;

		LOG.debug("UnclaimedFundsReposiotryImpl - findBySearchCriteria()");

		Map<String, Condition> scanFilter = new HashMap<String, Condition>();

		if (lastName != null && lastName.length() > 0) {
			paramCount += 1;
			scanFilter.put("lastName", new Condition().withAttributeValueList(new AttributeValue(lastName.toUpperCase()))
					.withComparisonOperator(ComparisonOperator.CONTAINS));
		}
		if (city != null && city.length() > 0) {
			paramCount += 1;
			scanFilter.put("city", new Condition().withAttributeValueList(new AttributeValue(city.toUpperCase()))
					.withComparisonOperator(ComparisonOperator.CONTAINS));
		}
		if (state != null && state.length() > 0) {
			paramCount += 1;
			scanFilter.put("stateCd", new Condition().withAttributeValueList(new AttributeValue(state.toUpperCase()))
					.withComparisonOperator(ComparisonOperator.CONTAINS));
		}
		if (zip != null && zip.length() > 0) {
			paramCount += 1;
			scanFilter.put("zipCd", new Condition().withAttributeValueList(new AttributeValue(zip.toUpperCase()))
					.withComparisonOperator(ComparisonOperator.CONTAINS));
		}

		if (paramCount > 1)
			scanRequest.setConditionalOperator(ConditionalOperator.AND);
		
		scanRequest.setScanFilter(scanFilter);
		
		Calendar cal = Calendar.getInstance();
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

		LOG.info("Query Started at :: " + sdf.format(cal.getTime()));
		
		ScanResult scanResult = dbHelper.getDynamoDBInstance().scan(scanRequest);
		long itemCount = scanResult.getCount();
		
		LOG.info("itemCount begin  :" + itemCount);
		
		List<Map<String, AttributeValue>> results = new ArrayList<Map<String, AttributeValue>>();;
		results.addAll(scanResult.getItems());
		
		while(scanResult.getLastEvaluatedKey() != null && !scanResult.getLastEvaluatedKey().isEmpty()) {
			scanResult = dbHelper.getDynamoDBInstance().scan(scanRequest.withExclusiveStartKey(scanResult.getLastEvaluatedKey()));	
			results.addAll(scanResult.getItems());	
		}		
		
		LOG.info("Query ended at :: " + sdf.format(cal.getTime()));
		
		itemCount += scanResult.getCount();
		LOG.info("itemCount end  :" + itemCount);
		
		LOG.debug("before call populateUnclaimedFunds :" + results.size());
		LOG.info("before call populateUnclaimedFunds  :" + results.size());

		List<UnclaimedFunds> unclaimedFunds = null;

		if (results != null && results.size() > 0) {
			
			LOG.info("populate UnclaimedFunds begin :: " + sdf.format(cal.getTime()));
			unclaimedFunds = populateUnclaimedFunds(results);	
			LOG.info("populate UnclaimedFunds end :: " + sdf.format(cal.getTime()));
			
			LOG.debug("after call populateUnclaimedFunds"+ unclaimedFunds.size());
			LOG.info("after call populateUnclaimedFunds"+ unclaimedFunds.size());
		}		

		return unclaimedFunds;

	}

	@Override
	public List<FailedBatch> saveUnClaimedFunds(Set<UnClaimedFund> unClaimedFunds) {
		
		DynamoDBMapper mapper = dbHelper.getDynamoDBMapper();		
		AmazonDynamoDB client = dbHelper.getDynamoDBInstance();
		DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withTableNameOverride(TableNameOverride.withTableNameReplacement(globalProperties.getTable()))
	        .build();
	    mapper = new DynamoDBMapper(client, mapperConfig);	   
		
		return mapper.batchSave(unClaimedFunds);
	}

	@Override
	public void resetTable() {
		DynamoDB dynamoDB = new DynamoDB(dbHelper.getDynamoDBInstance());
		Table table = dynamoDB.getTable(globalProperties.getTable());
		table.delete();
		try {
			table.waitForDelete();
		} catch (InterruptedException e) {
			LOG.error("Cannot perform table delete : " + e.getMessage());
		}

		List<AttributeDefinition> attributeDefinitions = new ArrayList<AttributeDefinition>();
		attributeDefinitions
				.add(new AttributeDefinition().withAttributeName("unclaimedFundNumber").withAttributeType("S"));

		List<KeySchemaElement> keySchema = new ArrayList<KeySchemaElement>();
		keySchema.add(new KeySchemaElement().withAttributeName("unclaimedFundNumber").withKeyType(KeyType.HASH));

		CreateTableRequest request = new CreateTableRequest().withTableName(globalProperties.getTable())
				.withKeySchema(keySchema).withAttributeDefinitions(attributeDefinitions).withProvisionedThroughput(
						new ProvisionedThroughput().withReadCapacityUnits(5L).withWriteCapacityUnits(5L));

		table = dynamoDB.createTable(request);

		try {
			table.waitForActive();
		} catch (InterruptedException e) {
			LOG.error("Cannot perform table creation: " + e.getMessage());
		}
	}

	private List<UnclaimedFunds> populateUnclaimedFunds(final List<Map<String, AttributeValue>> unClaimedFunds) {

		LOG.debug("populateUnclaimedFunds  :" + unClaimedFunds.size());
		
		List<UnclaimedFunds> unclaimedFundsResponse = new ArrayList<>();
		
		for (Map<String, AttributeValue> item : unClaimedFunds) {
			
			String firstName ="";
			String middleInitial ="";
			String lastName ="";			
			
			UnclaimedFunds unclaimedFund = new UnclaimedFunds();			

			if (item.containsKey("firstName") && item.get("firstName").getS() != null) {
				firstName = item.get("firstName").getS();
				unclaimedFund.setFirstName(firstName);
			}else {
				unclaimedFund.setFirstName("");
			}
			
			if (item.containsKey("lastName") && item.get("lastName").getS() != null) {
				lastName = item.get("lastName").getS();
				unclaimedFund.setLastName(lastName);
			}else {
				unclaimedFund.setLastName("");
			}
			
			if (item.containsKey("middleInitial") && item.get("middleInitial").getS() != null) {
				middleInitial = item.get("middleInitial").getS();
				unclaimedFund.setMiddleInitial(middleInitial);
			}else {
				unclaimedFund.setMiddleInitial("");
			}				
				
			
			unclaimedFund.setFullName(getFullName(firstName, middleInitial, lastName));
						
			if (item.containsKey("address") && item.get("address").getS() != null) {
				unclaimedFund.setAddress(item.get("address").getS());
			}else {
				unclaimedFund.setAddress("");
			}
			
			if (item.containsKey("city") &&  item.get("city").getS() != null) {
				unclaimedFund.setCity(item.get("city").getS());
			}else {
				unclaimedFund.setCity("");
			}
			
			if (item.containsKey("stateCd") && item.get("stateCd").getS() != null) {				
				unclaimedFund.setStateCd(item.get("stateCd").getS());				
			}else {
				unclaimedFund.setStateCd("");
			}
			
			if (item.containsKey("zipCd") && item.get("zipCd").getS() != null) {
				unclaimedFund.setZipCd(item.get("zipCd").getS());
			}else {
				unclaimedFund.setZipCd("");
			}
			
			if(item.containsKey("businessIndicator") && item.get("businessIndicator").getS() != null) {
				unclaimedFund.setBusinessIndicator(item.get("businessIndicator").getS());
			}else {
				unclaimedFund.setBusinessIndicator("");
			}
			
			unclaimedFundsResponse.add(unclaimedFund);

		}
		return unclaimedFundsResponse;
	}
	
	private String getFullName(String first , String middle , String last) {
		String fullName = "";
		
		if(middle=="")		
			fullName = first + " " + last;
		else
			fullName = first + " " + middle + " " + last;		
		
		return fullName.trim();
	}
		

}
