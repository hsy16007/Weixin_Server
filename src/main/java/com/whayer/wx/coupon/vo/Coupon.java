package com.whayer.wx.coupon.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 优惠卷
 * @author duyu
 *
 */
public class Coupon implements Serializable{

	private static final long serialVersionUID = -8493923707627500819L;
	
	private String id;
	private String userId;         //发放者id
	private BigDecimal amount;     //金额
	private boolean isEffect;     //是否已使用
	private boolean isExpired;    //是否过期
	private Date createDate;      //创建日期
	private Date useDate;         //使用日期
	private String createUserId;  //创建人id
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	public BigDecimal getAmount() {
		return amount;
	}
	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}
	public boolean isEffect() {
		return isEffect;
	}
	public void setEffect(boolean isEffect) {
		this.isEffect = isEffect;
	}
	public boolean isExpired() {
		return isExpired;
	}
	public void setExpired(boolean isExpired) {
		this.isExpired = isExpired;
	}
	public Date getCreateDate() {
		return createDate;
	}
	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}
	public Date getUseDate() {
		return useDate;
	}
	public void setUseDate(Date useDate) {
		this.useDate = useDate;
	}
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
	public String getCreateUserId() {
		return createUserId;
	}
	public void setCreateUserId(String createUserId) {
		this.createUserId = createUserId;
	}
	@Override
	public String toString() {
		return "Coupon [id=" + id + ", userId=" + userId + ", amount=" + amount + ", isEffect=" + isEffect
				+ ", isExpired=" + isExpired + ", createDate=" + createDate + ", useDate=" + useDate + "]";
	}
	
}