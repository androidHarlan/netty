package io.netty.handler.codec.dns;

/**
 * Represents the possible response codes a server may send after
 * receiving a query. A response code of 0 indicates no error.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public enum ResponseCode {

	/**
	 * ID 0, no error
	 */
	NOERROR(0, "no error"),
	/**
	 * ID 1, format error
	 */
	FORMERROR(1, "format error"),
	/**
	 * ID 2, server failure
	 */
	SERVFAIL(2, "server failure"),
	/**
	 * ID 3, name error
	 */
	NXDOMAIN(3, "name error"),
	/**
	 * ID 4, not implemented
	 */
	NOTIMPL(4, "not implemented"),
	/**
	 * ID 5, operation refused
	 */
	REFUSED(5, "operation refused"),
	/**
	 * ID 6, domain name should not exist
	 */
	YXDOMAIN(6, "domain name should not exist"),
	/**
	 * ID 7, resource record set should not exist
	 */
	YXRRSET(7, "resource record set should not exist"),
	/**
	 * ID 8, rrset does not exist
	 */
	NXRRSET(8, "rrset does not exist"),
	/**
	 * ID 9, not authoritative for zone
	 */
	NOTAUTH(9, "not authoritative for zone"),
	/**
	 * ID 10, name not in zone
	 */
	NOTZONE(10, "name not in zone"),
	/**
	 * ID 11, bad extension mechanism for version
	 */
	BADVERS(11, "bad extension mechanism for version"),
	/**
	 * ID 12, bad signature
	 */
	BADSIG(12, "bad signature"),
	/**
	 * ID 13, bad key
	 */
	BADKEY(13, "bad key"),
	/**
	 * ID 14, bad timestamp
	 */
	BADTIME(14, "bad timestamp");

	private final int id;
	private final String message;

	/**
	 * Returns a formated message for an error received given an ID, or unknown
	 * if the error is unsupported.
	 * 
	 * @param id ID of the error code.
	 * @return Formatted error message.
	 */
	public static String get(int id) {
		ResponseCode[] errors = ResponseCode.values();
		for (ResponseCode e : errors) {
			if (e.id == id) {
				return e.name() + ": type " + e.id + ", " + e.message;
			}
		}
		return "UNKNOWN: unknown error";
	}

	private ResponseCode(int id, String message) {
		this.id = id;
		this.message = message;
	}

}