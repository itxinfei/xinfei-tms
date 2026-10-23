package com.xinfei.vo.base.transforCenter.business;

import com.xinfei.vo.base.angency.AgencyVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "车辆信息")
public class TruckVo implements Serializable {
    private static final long serialVersionUID = 8540398115062991784L;
    @Schema(description = "id")
    private String id;
    @Schema(description = "车辆类型")
    private TruckTypeVo truckType;
    @Schema(description = "所属车队")
    private FleetVo fleet;
    @Schema(description = "品牌")
    private String brand;
    @Schema(description = "车牌号码")
    private String licensePlate;
    @Schema(description = "GPS设备id")
    private String deviceGpsId;
    @Schema(description = "准载重量")
    private BigDecimal allowableLoad;
    @Schema(description = "准载体积")
    private BigDecimal allowableVolume;
    @Schema(description = "车辆行驶证信息id")
    private String truckLicenseId;
    @Schema(description = "所属机构信息")
    private AgencyVo agency;
    @Schema(description = "工作状态")
    private String workStatus;
    @Schema(description = "过期状态")
    private String expireStatus;
    @Schema(description = "装载状态")
    private String loadStatus;
    @Schema(description = "在线状态：0-离线 1-在线（只读，由北斗/GPS 心跳维护，保存车辆时忽略入参）")
    private Integer onlineStatus;
    @Schema(description = "最后心跳时间（只读，由北斗/GPS 心跳维护，保存车辆时忽略入参）")
    private LocalDateTime lastHeartbeatTime;
}
