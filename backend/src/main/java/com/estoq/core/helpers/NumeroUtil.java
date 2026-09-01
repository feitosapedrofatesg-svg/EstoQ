package com.estoq.core.helpers;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NumeroUtil {

	private NumeroUtil() {
	}

	public static final int MONEY_SCALE = 2;

	public static BigDecimal zero() {
		return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	public static boolean negativo(BigDecimal valor) {
		return valor != null && valor.signum() < 0;
	}

	public static BigDecimal s(BigDecimal v) {
		return v == null ? BigDecimal.ZERO : v;
	}

	public static BigDecimal money(BigDecimal v) {
		return s(v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	public static BigDecimal soma(BigDecimal a, BigDecimal b) {
		return s(a).add(s(b));
	}

	public static BigDecimal subtrai(BigDecimal a, BigDecimal b) {
		return s(a).subtract(s(b));
	}

	public static BigDecimal multiplica(BigDecimal a, BigDecimal b) {
		return s(a).multiply(s(b));
	}

	public static BigDecimal divide(BigDecimal a, BigDecimal b, int scale) {
		if (b == null || b.signum() == 0) {
			return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
		}
		return s(a).divide(b, scale, RoundingMode.HALF_UP);
	}

	public static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
		if (total == null || total.signum() == 0) {
			return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
		}
		return divide(parte, total, 4);
	}
}