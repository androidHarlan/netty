package bakkar.mohamed.dnscodec;

public enum ResponseCode {
	NOERROR(0, "no error"),
	FORMERROR(1, "format error"),
	SERVFAIL(2, "server failure"),
	NXDOMAIN(3, "name error"),
	NOTIMPL(4, "not implemented"),
	REFUSED(5, "connection refused"),
	YXDOMAIN(6, "domain name should not exist"),
	YXRRSET(7, "resource record set should not exist"),
	NXRRSET(8, "rrset does not exist"),
	NOTAUTH(9, "not authoritative for zone"),
	NOTZONE(10, "name not in zone"),
	BADVERS(11, "bad extension mechanism for version"),
	BADSIG(12, " bad signature"),
	BADKEY(13, " bad key"),
	BADTIME(14, "bad timestamp");

	private final int id;
	private final String message;

	public static String get(int id) {
		Error[] errors = Error.values();
		for (Error e : errors) {
			if (e.id == id) {
				return e.name() + ": type " + e.id + ", " + e.message;
			}
		}
		return "UNKNOWN: unknown error";
	}

	private Error(int id, String message) {
		this.id = id;
		this.message = message;
	}

}