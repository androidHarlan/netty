/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.dns;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.Future;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

import bakkar.mohamed.dnscodec.Question;

public class DnsTransmission implements Callable<byte[]> {

	private static final Random random = new Random();
	private static final DefaultEventExecutorGroup executor = new DefaultEventExecutorGroup(4);
	private static final Map<Integer, DnsTransmission> resolvers = new HashMap<Integer, DnsTransmission>();

	public static DnsTransmission forId(int id) {
		synchronized (resolvers) {
			return resolvers.get(id);
		}
	}

	private static int generateShort() {
		return random.nextInt(Short.MAX_VALUE + 1);
	}

	private final byte[] dnsAddress;
	private final String name;
	private final int id;
	private final Question question;

	private byte[] result = null;

	public DnsTransmission(byte[] dnsAddress, Question question) {
		this.dnsAddress = dnsAddress;
		this.name = question.name();
		this.question = question;
		int id;
		synchronized (resolvers) {
			for (id = generateShort(); resolvers.containsKey(id); id = generateShort());
			this.id = id;
			resolvers.put(id, this);
		}
	}

	@Override
	public byte[] call() {
		byte[] cached = DnsCache.obtainAnswerData(name);
		if (cached == null) {
			try {
				DnsClient.resolveQuery(this);
			} catch (UnknownHostException | InterruptedException e) {
				e.printStackTrace();
			}
			return result;
		}
		return cached;
	}

	public Future<byte[]> submitQuery() throws Exception {
		return executor.submit(this);
	}

	public byte[] dnsAddress() {
		return dnsAddress;
	}

	public int id() {
		return id;
	}

	public String name() {
		return name;
	}

	public Question question() {
		return question;
	}

	public void setResult(byte[] result) {
		this.result = result;
	}

}
