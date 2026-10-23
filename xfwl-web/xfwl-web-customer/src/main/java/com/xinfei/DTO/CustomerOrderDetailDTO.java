package com.xinfei.DTO;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Data
@ToString
@Builder
public class CustomerOrderDetailDTO {
    /**
     * 订单号
     */
    private String id;
    /**
     * 订单状态
     */
    private int status;
    /**
     * 运单号
     */
    private String tranOrderId;
    /**
     * 计划取件时间
     */
    private LocalDateTime planPickUpTime;

    /**
     * 派送完成时间
     */
    private LocalDateTime actualDispathedTime;

    /**
     * 取消时间
     */
    private LocalDateTime cancelTime;

    /**
     * 签收状态：1签收 2拒收（来自派件任务 sign_status）
     */
    private Integer signStatus;

    /**
     * 签收照片URL列表，最多3张；客户端不返回手写签名图
     */
    private List<String> signPictures;

    /**
     * 签收备注/拒收原因（签收=备注，拒收时为拒收原因）
     */
    private String signRemark;

    /**
     * 姓名
     */
    private String name;
    /**
     * 电话
     */
    private String mobile;
    /**
     * 头像
     */
    private String avatar;
}
