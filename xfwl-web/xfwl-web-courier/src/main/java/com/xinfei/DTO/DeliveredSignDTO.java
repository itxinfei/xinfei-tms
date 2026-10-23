package com.xinfei.DTO;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 妥投（签收/拒收）凭证入参（快递员端 App）
 *
 * <p>对应接口 PUT /courier/delivered/{tranOrderId}/{status} 的请求体；
 * 条件必填规则在 CourierController.delivered 方法开头手写校验（web-courier 模块无 bean validation 触发先例，
 * 故本类不声明校验注解，约束以控制器手写为准）：</p>
 * <ul>
 *     <li>status=1 签收：{@link #signSignature} 必填（非空白）</li>
 *     <li>status=0 拒收：{@link #signRemark} 必填（非空白，即拒收原因）</li>
 *     <li>{@link #signPictures} 过滤空白元素后最多 3 个；签名图/备注最长 500 字符</li>
 * </ul>
 * <p>请求体声明为 {@code @RequestBody(required = false)}：完全不带 body 不会触发消息解析异常，
 * 但缺失条件必填凭证时仍返回 400——不存在无凭证妥投的兼容口子，旧版 App 须随本批升级。</p>
 */
@Data
public class DeliveredSignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 签收照片URL列表，最多3张；选填，拒收时也可不传
     */
    private List<String> signPictures;

    /**
     * 手写签名图片URL，签收时必填（非空白）
     */
    private String signSignature;

    /**
     * 签收备注/拒收原因，拒收时必填（非空白）
     */
    private String signRemark;
}
