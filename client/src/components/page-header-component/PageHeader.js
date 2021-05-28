import { Link, useHistory } from "react-router-dom"
import { PageHeader, Menu,Avatar,Image, Space } from 'antd';
import { MailOutlined,HomeOutlined,EditOutlined, UserOutlined,CalendarOutlined, LogoutOutlined } from '@ant-design/icons';
import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import RandomColor from "../../constants/RandomColor";
import moment from "moment";
import {LogOutUser} from '../../store/actions/Action';

const { SubMenu } = Menu;




function PollPageHeader(){
const dispatch=useDispatch();
const history=useHistory()
const[current,setCurrent]=useState("home")

const HandleClick = e => {
    console.log('click ', e);
    setCurrent(e.key);
 };
      const auth=useSelector(state=>state.auth)

const RenderAvatar=()=>{
    if(auth.user.avatar==="none"){
        return(<Avatar style={{backgroundColor:RandomColor()}}
             shape ="circle" >
                 {auth.user.UserName.charAt(0).toUpperCase()}
             </Avatar>
             
             )
    }
    else{
        return(<Avatar shape ="circle"  src={auth.user.Avatar}/>)
    }

}

const RenderPageHeader=()=>{
    if(auth.isAuthenticated===true){
        const createdAt= moment(auth.user.CreatedAt).format("LLLL");
     return(
        <PageHeader style={{position: "sticky",top:0,zIndex:1001}}
        ghost={false}
        onBack={() => history.goBack()}
        title="Polling App"
        subTitle=""
        extra={[
          <Menu onClick={HandleClick} selectedKeys={[current]} mode="horizontal" >
          <Menu.Item key="home" icon={<HomeOutlined />}>
          <Link to="/dashboard">
            Home
              </Link>
          </Menu.Item>
          <Menu.Item key="create-poll"  icon={<EditOutlined/>}>
          <Link to="/create_poll">
            Create Poll
              </Link>
          </Menu.Item>
          <SubMenu key="SubMenu"  title={<RenderAvatar/>}>
          <Menu.ItemGroup style={{fontWeight:'bold'}} title="Profile">
          <Menu.Item key="setting:1"><UserOutlined/><Space/>{auth.user.UserName}</Menu.Item>
          <Menu.Item key="setting:2"><MailOutlined/><Space/>{auth.user.Email}</Menu.Item>
          <Menu.Item key="setting:3"><CalendarOutlined/><Space/>{createdAt}</Menu.Item>
          </Menu.ItemGroup>
          <Menu.ItemGroup style={{fontWeight:'bold'}} title="Logout">
          <Menu.Item key="setting:4" onClick={()=>{dispatch(LogOutUser())}}><LogoutOutlined/><Space/>Exit</Menu.Item>
           </Menu.ItemGroup>
          </SubMenu>
          </Menu>,
        ]}
      ></PageHeader>  
        )   
    }
    
    else{
        return(
        <div></div>
        );
    }
}
    return(
       <RenderPageHeader/>
     );
        
}
export default PollPageHeader;