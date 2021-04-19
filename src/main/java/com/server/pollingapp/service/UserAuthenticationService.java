package com.server.pollingapp.service;

import com.server.pollingapp.models.AddressModel;
import com.server.pollingapp.models.UserModel;
import com.server.pollingapp.repository.UserRepository;
import com.server.pollingapp.request.LoginRequest;
import com.server.pollingapp.request.RealTimeLogRequest;
import com.server.pollingapp.request.RegistrationRequest;
import com.server.pollingapp.response.LoginResponse;
import com.server.pollingapp.response.UniversalResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;


/**
 * @author Jos Wambugu
 * @since 13-04-2021
 * @apiNote <p>
 *     <b> The UserAuthenticationService should only contain RegisterUser(signup),LoginUser(signIn) and validate access token methods).</b>
 *
 *     <li>RegisterUser-> Collects User Details,Validates them,Encrypts password,Saves details to db and sends an email to activate account</li>
 *     <li>LoginUser->Collects Login Credentials,if credentials are correct it generates a jwtToken for session Mgmt</li>
 *     <li>ValidateAccount-> Once Registered an Email is Sent containing a link,
 *                         link contains token which should be validated.This is to verify that the account user is real.
 *      </li>
 *
 * </p>
 */
@Service
public class UserAuthenticationService {

    @Autowired
    UserRepository userRepository;
    @Autowired
    BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    JwtService jwtService;
    @Autowired
    PollStream pollStream;
    @Autowired
    EmailService emailService;

    public ResponseEntity<UniversalResponse>RegisterUser(RegistrationRequest registrationRequest){
        // CHECK IF EMAIL OR USERNAME EXISTS
        if (userRepository.existsByEmail(registrationRequest.getEmail())){
            UniversalResponse details = new UniversalResponse();
            details.setMessage("Email Already Exists");
            details.setError(true);

            //GENERATE LOGS
            pollStream.sendToMessageBroker(new RealTimeLogRequest("WARN","Someone tried Registering" +
                    "With an Email That Already exists","UserAuthenticationService"));
            return ResponseEntity.badRequest().body(details);
        }
        else if (userRepository.existsByUsername(registrationRequest.getUsername())){
            UniversalResponse details = new UniversalResponse();
            details.setMessage("UserName Already Exists");
            details.setError(true);

            //GENERATE LOGS
            pollStream.sendToMessageBroker(new RealTimeLogRequest("WARN","Someone tried Registering" +
                    "With a UserName That Already exists","UserAuthenticationService"));
            return ResponseEntity.badRequest().body(details);

        }
        //OTHERWISE OF EVERYTHING IS FINE ,ENCRYPT PASSWORD,SAVE USER AND SEND THEM A CONFIRMATION EMAIL
        String password=bCryptPasswordEncoder.encode(registrationRequest.getPassword());

        AddressModel addressModel=new AddressModel(registrationRequest.getCity(), registrationRequest.getCountry());
        UserModel newUser=new UserModel(registrationRequest.getUsername(), registrationRequest.getEmail(),
                         password,addressModel );

        userRepository.save(newUser);

        //GENERATE LOGS
        pollStream.sendToMessageBroker(new RealTimeLogRequest("INFO", registrationRequest.getUsername()+" "+"Has Successfully Been Registered","UserAuthenticationService"));


        //CREATE ACTIVATION TOKEN
        String activationToken=jwtService.GenerateAccountActivationToken(registrationRequest.getUsername());

        //SEND EMAIL WITH LINK->TODO
        emailService.createActivationTemplate(activationToken,registrationRequest);

        //GENERATE LOGS
        pollStream.sendToMessageBroker(new RealTimeLogRequest("INFO", registrationRequest.getUsername()+" "+"Has Received An Email","UserAuthenticationService"));

        //SEND SUCCESS MESSAGE AFTER REGISTERING USER
        UniversalResponse success=new UniversalResponse();
        success.setMessage("Please Check your Email To Activate your Account");
        success.setError(false);
        return ResponseEntity.ok().body(success);

    }

    public ResponseEntity<LoginResponse> LoginUser(LoginRequest loginRequest){
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(),
                    loginRequest.getPassword()));
        }
        catch (DisabledException e){
            //GENERATE LOGS
            pollStream.sendToMessageBroker(new RealTimeLogRequest("WARN", loginRequest.getUsername()+" "+"Tried To Login with a Disabled Account","UserAuthenticationService"));
            //RETURN 404 ERROR
            LoginResponse loginResponse=new LoginResponse();
            loginResponse.setError(true);
            loginResponse.setMessage("Your Account Has Not been Activated");
            loginResponse.setToken(null);
            return ResponseEntity.badRequest().body(loginResponse);

        }
        catch (LockedException e){
            //GENERATE LOGS
            pollStream.sendToMessageBroker(new RealTimeLogRequest("WARN", loginRequest.getUsername()+" "+"Tried To Login with a Disabled Account","UserAuthenticationService"));
            //RETURN 404 ERROR
            LoginResponse loginResponse=new LoginResponse();
            loginResponse.setError(true);
            loginResponse.setMessage("Your Account Has Not Yet Been Activated");
            loginResponse.setToken(null);
            return ResponseEntity.badRequest().body(loginResponse);

        }
        catch (BadCredentialsException e){
            //GENERATE LOGS
            pollStream.sendToMessageBroker(new RealTimeLogRequest("WARN", loginRequest.getUsername()+" "+"Submitted Invalid Login Credentials","UserAuthenticationService"));
            //RETURN 404 ERROR
            LoginResponse loginResponse=new LoginResponse();
            loginResponse.setError(true);
            loginResponse.setMessage("Invalid Credentials");
            loginResponse.setToken(null);
            return ResponseEntity.badRequest().body(loginResponse);

        }
        //AFTER SUCCESSFUL AUTHENTICATION CREATE A JWT TOKEN
        UserModel user= userRepository.findByUsername(loginRequest.getUsername());
        String jwtToken=jwtService.GenerateLoginToken(user);
        //GENERATE LOGS
        pollStream.sendToMessageBroker(new RealTimeLogRequest("INFO", loginRequest.getUsername()+" "+"Successfully Logged In","UserAuthenticationService"));
        //RETURN 200 SUCCESS
        LoginResponse loginResponse=new LoginResponse();
        loginResponse.setError(false);
        loginResponse.setMessage("Successfully Logged In");
        loginResponse.setToken("Bearer"+" "+jwtToken);
        return ResponseEntity.ok().body(loginResponse);

    }
    public ResponseEntity<UniversalResponse> ActivateUserAccount(String token){
        //CHECK IF TOKEN IS EXPIRED
        if (!jwtService.ValidateToken(token)){
            UniversalResponse response= new UniversalResponse();
            response.setError(true);
            response.setMessage("Your Activation Token No longer works");
            //GENERATE LOG
            pollStream.sendToMessageBroker(new RealTimeLogRequest("WARN","Activation Token Is Expired","UserAuthenticationService"));
            return ResponseEntity.badRequest().body(response);
        }

        //EXTRACT DETAILS
        String username = jwtService.ExtractUserName(token);
        UserModel user= userRepository.findByUsername(username);
        //CHECK IF USER HAD ALREADY VALIDATED
        if (user.getEnabled()){
            //RETURN ERROR SAYING ACCOUNT WAS ALREADY ACTIVATED
            UniversalResponse response= new UniversalResponse();
            response.setError(true);
            response.setMessage("Your Account was already activated");
            //GENERATE LOG
            pollStream.sendToMessageBroker(new RealTimeLogRequest("WARN",username+" "+"Tried to activate Account again","UserAuthenticationService"));
            return ResponseEntity.badRequest().body(response);
        }
        //IF NOT EXPIRED,NOT VALIDATE EXTRACT USER-DETAILS AND SET ENABLED TO TRUE
        user.setEnabled(true);
        user.setAccountNotLocked(true);

        //UPDATE USER CONTENTS
        userRepository.save(user);

        //RETURN SUCCESS MESSAGE
        UniversalResponse response= new UniversalResponse();
        response.setError(false);
        response.setMessage("Your Account Has Been Activated Successfully");

        //GENERATE LOG
        pollStream.sendToMessageBroker(new RealTimeLogRequest("INFO",username+" "+"has activated their account","UserAuthenticationService"));
        return ResponseEntity.ok().body(response);
    }
}
