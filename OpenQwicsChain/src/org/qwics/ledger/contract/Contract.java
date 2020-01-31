/*
QWICSchain Enterprise Blockchain framework for Java EE

Copyright (c) 2019 Philipp Brune    Email: Philipp.Brune@qwics.org

QWICSchain is free software. You can redistribute it and/or modify it under the  
terms of the GNU General Public License as published by the Free Software Foundation, 
either version 3 of the License, or (at your option) any later version.               

It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or F
ITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more 
details.
                                                                                      
You should have received a copy of the GNU General Public License 
along with this project. If not, see <http://www.gnu.org/licenses/>. 

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of the software nor the names of its contributors may not be
used to endorse or promote products derived from this software without specific
prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS  AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
*/

package org.qwics.ledger.contract;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.qwics.ledger.Account;
import org.qwics.ledger.Asset;

import groovy.lang.GroovyClassLoader;

public class Contract {
	private GroovyClassLoader gcl = null;
	private Object contractObj = null;
	private ContractIOProvider provider = null;
	private long ownerAccountId;

	public Contract() {
		super();
	}

	public final void init(long ownerAccountId, String sourceCode) throws Exception {
		this.ownerAccountId = ownerAccountId;

		ImportCustomizer imports = new ImportCustomizer();
		imports.addImports("org.qwics.ledger.contract.Contract", "org.qwics.ledger.Asset", "org.qwics.ledger.Account",
				"org.qwics.ledger.contract.ContractIOProvider", "org.qwics.ledger.contract.TaData", "org.qwics.ledger.contract.TaList");
		SecureASTCustomizer secure = new SecureASTCustomizer();
		secure.setClosuresAllowed(false);
		secure.setMethodDefinitionAllowed(true);
		secure.setIndirectImportCheckEnabled(true);
		secure.setImportsWhitelist(Arrays.asList(new String[] { "org.qwics.ledger.contract.Contract",
				"org.qwics.ledger.Asset", "org.qwics.ledger.Account", "java.lang.Object", "java.io.Serializable",
				"org.qwics.ledger.contract.ContractIOProvider", "org.qwics.ledger.contract.TaData", "org.qwics.ledger.contract.TaList" }));
		secure.setStarImportsWhitelist(Arrays.asList(new String[] { "java.math.*", "java.util.*" }));
		secure.setConstantTypesClassesWhiteList(
				Arrays.asList(new Class[] { Boolean.class, boolean.class, Collection.class, Double.class, double.class,
						Float.class, float.class, Integer.class, int.class, Long.class, long.class, Object.class,
						String.class, BigDecimal.class, BigInteger.class, Asset.class, Account.class, byte[].class,
						List.class, ArrayList.class, Date.class, Serializable.class, ContractIOProvider.class, TaData.class, TaList.class }));
//		secure.setReceiversClassesWhiteList(Arrays.asList(new Class[] { Boolean.class, Collection.class, Integer.class,
//				Iterable.class, Object.class, Set.class, String.class, BigDecimal.class, BigInteger.class, Asset.class, byte[].class, List.class, ArrayList.class, Serializable.class, ContractIOProvider.class }));

		CompilerConfiguration config = new CompilerConfiguration();
		config.addCompilationCustomizers(imports, secure);
		gcl = new GroovyClassLoader(this.getClass().getClassLoader(), config);
		// System.err.println(sourceCode);
		Class clazz = gcl.parseClass(sourceCode);
		contractObj = clazz.newInstance();
		try {
			Method method = contractObj.getClass().getMethod("setOwnerAccountId", long.class);
			method.invoke(contractObj, this.ownerAccountId);
		} catch (InvocationTargetException e) {
		} catch (NoSuchMethodException e2) {
		}
	}

	protected final byte[] getSecureHash(String val) throws Exception {
		MessageDigest md = MessageDigest.getInstance("RIPEMD320", "FlexiCore");
		md.update(val.getBytes());
		return md.digest();
	}

	protected final boolean verify(Asset asset) {
		return provider.verify(asset);
	}

	protected final Double settlePrice(TaList buy, TaList sell) {
		List<Double> pb = buy.getPrices();
		List<Double> ps = sell.getPrices();
		BigDecimal vol = new BigDecimal(0.0);
		Double price = 0.0;
		
		BigDecimal sumBuy = new BigDecimal(0.0);
		for (int i = pb.size()-1; i >= 0; i--) {
			BigDecimal sum = new BigDecimal(0.0);
			for (int j = 0; j < ps.size(); j++) {
				if (ps.get(j) <= pb.get(i)) {
					sum = sum.add(sell.getAmount(ps.get(j)));						
				} else {
					break;
				}
			}			
			sumBuy = sumBuy.add(buy.getAmount(pb.get(i)));						
			if (sum.compareTo(sumBuy) > 0) {
				sum = sumBuy;
			}
			if (sum.compareTo(vol) > 0) {
				price = pb.get(i);
				vol = sum;
			}
		}
		
		return price;		
	}

	protected final Account getAccount(Long id) {
		return provider.getAccount(id);
	}

	protected final Account myAccount() {
		return provider.getAccount(this.ownerAccountId);
	}

	protected final void log(String msg) {
		provider.log(msg);
	}

	protected final void send(Long receiverId, Integer action, BigDecimal coins, Asset asset, byte[] data) {
		provider.send(ownerAccountId, receiverId, action, coins, asset, data);
	}

	public void receive(long senderId, int action, BigDecimal coins, Asset asset, byte[] data) throws Exception {
		try {
			System.out.println("receive "+action);
			Method method = contractObj.getClass().getMethod("receive", Long.class, Integer.class, BigDecimal.class,
					Asset.class, byte[].class);
			method.invoke(contractObj, senderId, action, coins, asset, data);
		} catch (InvocationTargetException e) {
		} catch (NoSuchMethodException e2) {
		}
	}

	public void onHour() throws Exception {
		try {
			Method method = contractObj.getClass().getMethod("onHour");
			method.invoke(contractObj);
		} catch (InvocationTargetException e) {
		} catch (NoSuchMethodException e2) {
		}
	}

	protected final void save(String identifier, Serializable object) {
		provider.save(ownerAccountId, identifier, object);
	}

	protected final Serializable load(String identifier) {
		return provider.load(ownerAccountId, identifier);
	}

	public static void main(String[] args) throws Exception {
		Contract c = new Contract();
		c.init(0, "import java.util.Arrays; \n class Echo extends Contract {\n" + " String str = \"test\" \n"
				+ "	void receive(Long senderId, Integer action, BigDecimal coins, Asset asset, byte[] data) {\n"
				+ "		System.exit() \n List<Asset> a = (List<Asset>)getProvider().load(0,\"liste\") \n Arrays.sort(a) \n send(senderId, action, coins, asset, data)\n"
				+ "	}\n" + "}");
		c.receive(17, 1, null, null, null);
	}

	public ContractIOProvider getProvider() {
		return provider;
	}

	public void setProvider(ContractIOProvider provider) {
		this.provider = provider;
		if (contractObj instanceof Contract) {
			((Contract) contractObj).setProvider(provider);
		}
	}

	public long getOwnerAccountId() {
		return ownerAccountId;
	}

	public void setOwnerAccountId(long ownerAccountId) {
		this.ownerAccountId = ownerAccountId;
	}
}
